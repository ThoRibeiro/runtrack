package com.runtrack.user.usecases.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.event.UserDeleted;
import com.runtrack.user.event.UserProfileUpdated;
import com.runtrack.user.event.UserRegistered;
import com.runtrack.user.usecases.fixture.InMemoryUserRepository;
import com.runtrack.user.usecases.model.profile.AccountStatus;
import com.runtrack.user.usecases.model.profile.Email;
import com.runtrack.user.usecases.model.profile.Handle;
import com.runtrack.user.usecases.model.profile.Physiology;
import com.runtrack.user.usecases.model.profile.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class UserAccountsTest {

    private static final Instant NOW = Instant.parse("2026-08-29T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private InMemoryUserRepository users;
    private List<Object> published;
    private UserAccounts accounts;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        published = new ArrayList<>();
        ApplicationEventPublisher publisher = published::add;
        RandomGenerator random = new java.util.Random(42);
        accounts = new UserAccounts(users, publisher, CLOCK, random);
    }

    private UserId registerMarie() {
        return accounts.register(new Handle("marie"), new Email("marie@example.com"), "Marie");
    }

    private UserId registerActiveMarie() {
        UserId id = registerMarie();
        accounts.verifyEmail(id);
        published.clear();
        return id;
    }

    @Test
    void registersAProfileAndAnnouncesIt() {
        UserId id = registerMarie();

        assertThat(users.findById(id)).isPresent();
        assertThat(published).singleElement()
                .isInstanceOfSatisfying(UserRegistered.class, event -> {
                    assertThat(event.userId()).isEqualTo(id);
                    assertThat(event.handle()).isEqualTo("marie");
                    assertThat(event.registeredAt()).isEqualTo(NOW);
                });
    }

    @Test
    void refusesADuplicateHandle() {
        registerMarie();

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> accounts.register(new Handle("marie"), new Email("autre@example.com"), "Autre"))
                .extracting(ConflictException::code)
                .isEqualTo("HANDLE_TAKEN");
    }

    @Test
    void refusesADuplicateEmail() {
        registerMarie();

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> accounts.register(new Handle("paul"), new Email("marie@example.com"), "Paul"))
                .extracting(ConflictException::code)
                .isEqualTo("EMAIL_TAKEN");
    }

    @Test
    void confirmsTheEmailWithoutAnnouncingIt() {
        UserId id = registerMarie();
        published.clear();

        accounts.verifyEmail(id);

        assertThat(users.findById(id)).get().extracting(User::status).isEqualTo(AccountStatus.ACTIVE);
        assertThat(published).isEmpty();
    }

    @Test
    void announcesEveryProfileChangeSoCachesCanBeInvalidated() {
        UserId id = registerActiveMarie();

        accounts.updateProfile(id, "Marie D.", "Trail", null);

        assertThat(published).singleElement().isInstanceOf(UserProfileUpdated.class);
    }

    @Test
    void changesTheHandleWhenItIsFree() {
        UserId id = registerActiveMarie();

        accounts.changeHandle(id, new Handle("marie.court"));

        assertThat(users.findById(id)).get().extracting(User::handle).isEqualTo(new Handle("marie.court"));
    }

    @Test
    void keepingOnesOwnHandleIsNotAConflict() {
        UserId id = registerActiveMarie();

        accounts.changeHandle(id, new Handle("marie"));

        assertThat(users.findById(id)).get().extracting(User::handle).isEqualTo(new Handle("marie"));
    }

    @Test
    void refusesAHandleAlreadyTaken() {
        UserId marie = registerActiveMarie();
        accounts.register(new Handle("paul"), new Email("paul@example.com"), "Paul");

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> accounts.changeHandle(marie, new Handle("paul")))
                .extracting(ConflictException::code)
                .isEqualTo("HANDLE_TAKEN");
    }

    @Test
    void changesTheAccountScope() {
        UserId id = registerActiveMarie();

        accounts.changeAccountScope(id, AudienceScope.PRIVATE);

        assertThat(users.findById(id)).get().extracting(User::accountScope).isEqualTo(AudienceScope.PRIVATE);
        assertThat(published).singleElement().isInstanceOf(UserProfileUpdated.class);
    }

    /** La physiologie est sensible : sa mise à jour ne part pas dans un événement. */
    @Test
    void recordsPhysiologyWithoutBroadcastingIt() {
        UserId id = registerActiveMarie();
        var physiology = new Physiology(
                Optional.empty(), Optional.empty(), OptionalDouble.of(58), OptionalDouble.empty());

        accounts.recordPhysiology(id, physiology);

        assertThat(users.findById(id)).get().extracting(User::physiology).isEqualTo(physiology);
        assertThat(published).isEmpty();
    }

    @Test
    void deletesByAnonymisingAndAnnouncesIt() {
        UserId id = registerActiveMarie();

        accounts.delete(id);

        assertThat(users.findById(id)).get().satisfies(user -> {
            assertThat(user.status()).isEqualTo(AccountStatus.DELETED);
            assertThat(user.handle().value()).startsWith("deleted-");
        });
        assertThat(published).singleElement().isInstanceOf(UserDeleted.class);
    }

    /** Le profil reste adressable par son identifiant : les courses le référencent. */
    @Test
    void aDeletedProfileKeepsItsIdentifier() {
        UserId id = registerActiveMarie();

        accounts.delete(id);

        assertThat(accounts.byId(id).id()).isEqualTo(id);
        assertThat(users.size()).isEqualTo(1);
    }

    @Test
    void findsByHandle() {
        registerMarie();

        assertThat(accounts.byHandle(new Handle("marie")).displayName()).isEqualTo("Marie");
    }

    @Test
    void reportsAnUnknownProfile() {
        UserId unknown = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-00000000ffff"));

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> accounts.byId(unknown))
                .extracting(NotFoundException::code)
                .isEqualTo("USER_NOT_FOUND");
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> accounts.byHandle(new Handle("inconnu")));
    }

    @Test
    void searchesOnHandleAndDisplayName() {
        registerMarie();
        accounts.register(new Handle("paul"), new Email("paul@example.com"), "Paul Marion");

        assertThat(accounts.search("marie")).hasSize(1);
        assertThat(accounts.search("Marion")).hasSize(1);
        assertThat(accounts.search("mari")).hasSize(2);
        assertThat(accounts.search("zzz")).isEmpty();
    }

    @Test
    void aBlankSearchMatchesNothingRatherThanEverything() {
        registerMarie();

        assertThat(accounts.search("  ")).isEmpty();
        assertThat(accounts.search(null)).isEmpty();
    }

    @Test
    void aDeletedProfileNoLongerAppearsInSearch() {
        UserId id = registerActiveMarie();

        accounts.delete(id);

        assertThat(accounts.search("marie")).isEmpty();
    }
}
