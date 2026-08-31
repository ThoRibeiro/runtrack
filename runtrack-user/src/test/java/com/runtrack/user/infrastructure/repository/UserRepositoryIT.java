package com.runtrack.user.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.usecases.service.UserAccounts;
import com.runtrack.user.usecases.port.UserRepository;
import com.runtrack.user.usecases.model.profile.AccountStatus;
import com.runtrack.user.usecases.model.profile.BiologicalSex;
import com.runtrack.user.usecases.model.profile.Email;
import com.runtrack.user.usecases.model.profile.Handle;
import com.runtrack.user.usecases.model.profile.Physiology;
import com.runtrack.user.usecases.model.profile.User;
import com.runtrack.user.support.PostgresIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Le port de persistance contre une vraie base : migrations Flyway appliquées, schéma
 * validé par Hibernate, contraintes d'unicité réellement en vigueur.
 *
 * <p>Un dépôt simulé n'aurait rien dit de tout cela — c'est précisément ce qui casse en
 * production.
 */
class UserRepositoryIT extends PostgresIntegrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private UserAccounts accounts;

    private static Handle uniqueHandle(String prefix) {
        return new Handle(prefix + System.nanoTime() % 1_000_000);
    }

    @Test
    void storesAndReadsBackTheWholeAggregate() {
        Handle handle = uniqueHandle("marie");
        UserId id = accounts.register(handle, new Email(handle.value() + "@example.com"), "Marie");
        accounts.verifyEmail(id);
        accounts.updateProfile(id, "Marie D.", "Trail et route", "https://cdn/marie.png");
        accounts.recordPhysiology(id, new Physiology(
                Optional.of(LocalDate.of(1998, 4, 12)), Optional.of(BiologicalSex.FEMALE),
                OptionalDouble.of(58.5), OptionalDouble.of(165)));

        User reloaded = users.findById(id).orElseThrow();

        assertThat(reloaded.displayName()).isEqualTo("Marie D.");
        assertThat(reloaded.bio()).contains("Trail et route");
        assertThat(reloaded.avatarUrl()).contains("https://cdn/marie.png");
        assertThat(reloaded.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(reloaded.physiology().weightKilograms()).hasValue(58.5);
        assertThat(reloaded.physiology().sex()).contains(BiologicalSex.FEMALE);
        assertThat(reloaded.physiology().birthDate()).contains(LocalDate.of(1998, 4, 12));
    }

    @Test
    void findsByHandleAndByEmail() {
        Handle handle = uniqueHandle("paul");
        Email email = new Email(handle.value() + "@example.com");
        accounts.register(handle, email, "Paul");

        assertThat(users.findByHandle(handle)).isPresent();
        assertThat(users.findByEmail(email)).isPresent();
        assertThat(users.existsByHandle(handle)).isTrue();
        assertThat(users.existsByEmail(email)).isTrue();
    }

    /** L'index unique est la garantie qui survit à un import ou à un correctif manuel. */
    @Test
    void theDatabaseRefusesADuplicateHandle() {
        Handle handle = uniqueHandle("doublon");
        accounts.register(handle, new Email(handle.value() + "@example.com"), "Premier");

        assertThatThrownBy(() -> users.save(User.register(
                new UserId(java.util.UUID.randomUUID()), handle,
                new Email("autre-" + handle.value() + "@example.com"), "Second", java.time.Instant.now())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void readsSeveralProfilesInOneQuery() {
        Handle first = uniqueHandle("lot");
        Handle second = uniqueHandle("lot");
        UserId one = accounts.register(first, new Email(first.value() + "@example.com"), "Un");
        UserId two = accounts.register(second, new Email(second.value() + "@example.com"), "Deux");

        List<User> found = users.findAllById(List.of(one, two));

        assertThat(found).extracting(User::id).containsExactlyInAnyOrder(one, two);
    }

    @Test
    void searchIgnoresCaseAndSkipsDeletedAccounts() {
        Handle handle = uniqueHandle("chercheuse");
        UserId id = accounts.register(handle, new Email(handle.value() + "@example.com"), "Zoé Martin");
        accounts.verifyEmail(id);

        assertThat(users.search("ZOÉ MART", 10)).extracting(User::id).contains(id);

        accounts.delete(id);
        assertThat(users.search("Zoé Mart", 10)).extracting(User::id).doesNotContain(id);
    }

    @Test
    void anonymisationSurvivesAReload() {
        Handle handle = uniqueHandle("effacee");
        UserId id = accounts.register(handle, new Email(handle.value() + "@example.com"), "À effacer");
        accounts.verifyEmail(id);

        accounts.delete(id);
        User reloaded = users.findById(id).orElseThrow();

        assertThat(reloaded.status()).isEqualTo(AccountStatus.DELETED);
        assertThat(reloaded.handle().value()).startsWith("deleted-");
        assertThat(reloaded.email().value()).endsWith("@deleted.invalid");
        assertThat(reloaded.accountScope()).isEqualTo(AudienceScope.PRIVATE);
        assertThat(reloaded.physiology().isKnown()).isFalse();
    }
}
