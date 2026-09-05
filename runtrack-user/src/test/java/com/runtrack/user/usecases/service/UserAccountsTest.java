package com.runtrack.user.usecases.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.error.NotFoundException;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.FederatedProfile;
import com.runtrack.user.event.UserDeleted;
import com.runtrack.user.event.UserProfileUpdated;
import com.runtrack.user.event.UserRegistered;
import com.runtrack.user.usecases.fixture.InMemoryAvatarStore;
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
    private InMemoryAvatarStore avatars;
    private List<Object> published;
    private UserAccounts accounts;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        avatars = new InMemoryAvatarStore();
        published = new ArrayList<>();
        ApplicationEventPublisher publisher = published::add;
        RandomGenerator random = new java.util.Random(42);
        accounts = new UserAccounts(users, avatars, publisher, CLOCK, random);
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

    // --- Identités fédérées (§ docs/decisions-keycloak.md) --------------------------------

    private static final UserId FEDERATED =
            UserId.of("0198c4d2-7f31-7a42-9c55-1b2c3d4e5f60");

    private static FederatedProfile marieFromTheRealm() {
        return new FederatedProfile("marie@example.com", "Marie", true);
    }

    /** L'identifiant vient du jeton : c'est tout l'intérêt de ne pas tenir de correspondance. */
    @Test
    void opensAProfileUnderTheIdentifierTheProviderGave() {
        assertThat(accounts.provisionFederated(FEDERATED, marieFromTheRealm())).isTrue();

        assertThat(users.findById(FEDERATED)).isPresent();
        assertThat(published).singleElement().isInstanceOf(UserRegistered.class);
    }

    /** Appelé à chaque connexion : le second passage ne doit rien faire, ni rien publier. */
    @Test
    void openingTheSameProfileTwiceChangesNothing() {
        accounts.provisionFederated(FEDERATED, marieFromTheRealm());
        published.clear();

        assertThat(accounts.provisionFederated(FEDERATED, marieFromTheRealm())).isFalse();
        assertThat(published).isEmpty();
    }

    /** Le pseudo est provisoire, dérivé de l'identifiant, et respecte les règles du domaine. */
    @Test
    void derivesAProvisionalHandleFromTheIdentifier() {
        accounts.provisionFederated(FEDERATED, marieFromTheRealm());

        Handle handle = users.findById(FEDERATED).orElseThrow().handle();
        assertThat(handle.value()).isEqualTo("runner-0198c4d2");
    }

    /** Un pseudo dérivé déjà pris s'allonge : deux tirages au sort pourraient se répéter. */
    @Test
    void lengthensTheProvisionalHandleRatherThanFailing() {
        accounts.register(new Handle("runner-0198c4d2"), new Email("autre@example.com"), "Autre");

        accounts.provisionFederated(FEDERATED, marieFromTheRealm());

        assertThat(users.findById(FEDERATED).orElseThrow().handle().value())
                .isEqualTo("runner-0198c4d27f31");
    }

    /** Le fournisseur a vérifié l'adresse : la personne n'a pas à le prouver une seconde fois. */
    @Test
    void aVerifiedEmailOpensAnActiveAccount() {
        accounts.provisionFederated(FEDERATED, marieFromTheRealm());

        assertThat(users.findById(FEDERATED).orElseThrow().status())
                .isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void anUnverifiedEmailStillWaitsForItsConfirmation() {
        accounts.provisionFederated(FEDERATED,
                new FederatedProfile("marie@example.com", "Marie", false));

        assertThat(users.findById(FEDERATED).orElseThrow().status())
                .isEqualTo(AccountStatus.PENDING_VERIFICATION);
    }

    /**
     * Le scénario de prise de contrôle : rattacher une identité fédérée à un compte existant
     * sur la seule foi de l'adresse suffirait à s'emparer de ce compte.
     */
    @Test
    void refusesAnAddressThatAlreadyBelongsToSomeoneElse() {
        registerMarie();

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> accounts.provisionFederated(FEDERATED, marieFromTheRealm()))
                .withMessageContaining("autre compte");
    }

    /** Sans nom transmis, l'adresse en fait un : un profil sans nom d'affichage n'existe pas. */
    @Test
    void fallsBackOnTheAddressWhenTheProviderGivesNoName() {
        accounts.provisionFederated(FEDERATED,
                new FederatedProfile("marie@example.com", null, true));

        assertThat(users.findById(FEDERATED).orElseThrow().displayName()).isEqualTo("marie");
    }

    // --- Inscription classique ------------------------------------------------------------

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
    void storesAnUploadedPhotoAndPointsTheProfileAtIt() {
        UserId id = registerActiveMarie();

        String address = accounts.uploadAvatar(id, "image/jpeg", new byte[] {1, 2, 3},
                imageId -> "https://runtrack.test/media/v1/avatars/" + imageId);

        assertThat(address).startsWith("https://runtrack.test/media/v1/avatars/");
        assertThat(accounts.byId(id).avatarUrl()).contains(address);
        assertThat(avatars.size()).isEqualTo(1);
    }

    @Test
    void aSecondPhotoReplacesTheFirstRatherThanPilingUp() {
        UserId id = registerActiveMarie();
        accounts.uploadAvatar(id, "image/png", new byte[] {1}, imageId -> imageId);

        accounts.uploadAvatar(id, "image/png", new byte[] {2}, imageId -> imageId);

        // Une image par compte : sans cela, chaque essai de photo laisse la
        // précédente en base, sans plus rien pour la référencer.
        assertThat(avatars.size()).isEqualTo(1);
    }

    @Test
    void refusesWhatIsNotAnImageTheClientsCanShow() {
        UserId id = registerActiveMarie();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> accounts.uploadAvatar(id, "application/pdf", new byte[] {1}, imageId -> imageId));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> accounts.uploadAvatar(id, "image/png", new byte[0], imageId -> imageId));
        assertThat(avatars.size()).isZero();
    }

    @Test
    void refusesAPhotoTooLargeToBeAThumbnail() {
        UserId id = registerActiveMarie();
        byte[] tooBig = new byte[2 * 1024 * 1024 + 1];

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> accounts.uploadAvatar(id, "image/jpeg", tooBig, imageId -> imageId));
    }

    @Test
    void deletingAnAccountTakesItsPhotoWithIt() {
        UserId id = registerActiveMarie();
        accounts.uploadAvatar(id, "image/jpeg", new byte[] {1}, imageId -> imageId);

        accounts.delete(id);

        // Une photo qui survit à son propriétaire est une donnée personnelle orpheline.
        assertThat(avatars.size()).isZero();
    }

    @Test
    void aDeletedProfileNoLongerAppearsInSearch() {
        UserId id = registerActiveMarie();

        accounts.delete(id);

        assertThat(accounts.search("marie")).isEmpty();
    }
}
