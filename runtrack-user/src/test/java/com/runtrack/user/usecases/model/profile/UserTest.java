package com.runtrack.user.usecases.model.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final UserId ID = new UserId(UUID.fromString("018f4c1e-0000-7000-8000-000000000001"));
    private static final Instant REGISTERED = Instant.parse("2026-08-29T08:00:00Z");
    private static final Instant LATER = Instant.parse("2026-08-30T09:30:00Z");

    private static User registered() {
        return User.register(ID, new Handle("marie"), new Email("marie@example.com"), "Marie", REGISTERED);
    }

    private static User active() {
        User user = registered();
        user.verifyEmail(LATER);
        return user;
    }

    @Test
    void startsPendingVerificationAndPublic() {
        User user = registered();

        assertThat(user.status()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(user.accountScope()).isEqualTo(AudienceScope.PUBLIC);
        assertThat(user.physiology()).isEqualTo(Physiology.UNKNOWN);
        assertThat(user.avatarUrl()).isEmpty();
        assertThat(user.bio()).isEmpty();
        assertThat(user.registeredAt()).isEqualTo(REGISTERED);
        assertThat(user.id()).isEqualTo(ID);
    }

    @Test
    void becomesActiveOnceTheEmailIsConfirmed() {
        User user = registered();

        user.verifyEmail(LATER);

        assertThat(user.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.status().canAct()).isTrue();
    }

    @Test
    void refusesToConfirmTwice() {
        User user = active();

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> user.verifyEmail(LATER))
                .extracting(ConflictException::code)
                .isEqualTo("EMAIL_ALREADY_VERIFIED");
    }

    @Test
    void refusesToRegisterWithoutItsEssentials() {
        Handle handle = new Handle("marie");
        Email email = new Email("marie@example.com");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.register(null, handle, email, "Marie", REGISTERED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.register(ID, null, email, "Marie", REGISTERED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.register(ID, handle, null, "Marie", REGISTERED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.register(ID, handle, email, "Marie", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.register(ID, handle, email, "  ", REGISTERED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> User.register(ID, handle, email, "x".repeat(81), REGISTERED));
    }

    @Nested
    class Editing {

        /**
         * La photo se change seule.
         *
         * <p>C'est toute la raison d'un geste à part : l'écran qui téléverse une image ne connaît
         * ni le nom d'affichage ni la biographie, et passer par la mise à jour de profil les
         * écraserait avec du vide.
         */
        @Test
        void changingTheAvatarLeavesTheRestAlone() {
            User user = active();
            user.updateProfile("Marie D.", "Trail et bitume", "https://cdn/ancienne.jpg", LATER);

            user.changeAvatar("https://cdn/nouvelle.jpg", LATER);

            assertThat(user.avatarUrl()).contains("https://cdn/nouvelle.jpg");
            assertThat(user.displayName()).isEqualTo("Marie D.");
            assertThat(user.bio()).contains("Trail et bitume");
        }

        @Test
        void anEmptyAvatarRemovesThePicture() {
            User user = active();
            user.changeAvatar("https://cdn/photo.jpg", LATER);

            user.changeAvatar("   ", LATER);

            assertThat(user.avatarUrl()).isEmpty();
        }

        /** Même règle que le reste du profil : un compte non confirmé ne publie rien. */
        @Test
        void changingTheAvatarNeedsAnActiveAccount() {
            User user = registered();

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> user.changeAvatar("https://cdn/photo.jpg", LATER))
                    .satisfies(refused -> assertThat(refused.code()).isEqualTo("ACCOUNT_NOT_ACTIVE"));
        }

        @Test
        void updatesTheProfileAndTrimsIt() {
            User user = active();

            user.updateProfile("  Marie D.  ", "  Trail et route  ", "  https://cdn/x.png  ", LATER);

            assertThat(user.displayName()).isEqualTo("Marie D.");
            assertThat(user.bio()).contains("Trail et route");
            assertThat(user.avatarUrl()).contains("https://cdn/x.png");
        }

        @Test
        void clearsOptionalFieldsWhenBlank() {
            User user = active();
            user.updateProfile("Marie", "Une bio", "https://cdn/x.png", LATER);

            user.updateProfile("Marie", "  ", null, LATER);

            assertThat(user.bio()).isEmpty();
            assertThat(user.avatarUrl()).isEmpty();
        }

        @Test
        void refusesOversizedText() {
            User user = active();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> user.updateProfile("Marie", "x".repeat(501), null, LATER))
                    .withMessageContaining("Biographie");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> user.updateProfile("Marie", null, "x".repeat(2_001), LATER))
                    .withMessageContaining("avatar");
        }

        @Test
        void changesHandleAndVisibility() {
            User user = active();

            user.changeHandle(new Handle("marie.court"), LATER);
            user.changeAccountScope(AudienceScope.FOLLOWERS, LATER);

            assertThat(user.handle()).isEqualTo(new Handle("marie.court"));
            assertThat(user.accountScope()).isEqualTo(AudienceScope.FOLLOWERS);
        }

        @Test
        void recordsPhysiology() {
            User user = active();
            var physiology = new Physiology(
                    Optional.empty(), Optional.of(BiologicalSex.FEMALE), OptionalDouble.of(58), OptionalDouble.of(165));

            user.recordPhysiology(physiology, LATER);

            assertThat(user.physiology()).isEqualTo(physiology);
        }

        @Test
        void refusesNullsWhereAValueObjectIsExpected() {
            User user = active();

            assertThatIllegalArgumentException().isThrownBy(() -> user.changeHandle(null, LATER));
            assertThatIllegalArgumentException().isThrownBy(() -> user.changeAccountScope(null, LATER));
            assertThatIllegalArgumentException().isThrownBy(() -> user.recordPhysiology(null, LATER));
        }

        /** Un compte non confirmé, suspendu ou supprimé ne se modifie pas. */
        @Test
        void refusesEveryEditWhileTheAccountCannotAct() {
            User pending = registered();

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> pending.updateProfile("Marie", null, null, LATER))
                    .extracting(ConflictException::code)
                    .isEqualTo("ACCOUNT_NOT_ACTIVE");
            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> pending.changeHandle(new Handle("autre"), LATER));
            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> pending.changeAccountScope(AudienceScope.PRIVATE, LATER));
            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> pending.recordPhysiology(Physiology.UNKNOWN, LATER));
        }
    }

    @Nested
    class Moderation {

        @Test
        void suspendsAnAccount() {
            User user = active();

            user.suspend(LATER);

            assertThat(user.status()).isEqualTo(AccountStatus.SUSPENDED);
            assertThat(user.status().canAct()).isFalse();
        }

        @Test
        void cannotSuspendADeletedAccount() {
            User user = active();
            user.anonymize("abc123", LATER);

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> user.suspend(LATER))
                    .extracting(ConflictException::code)
                    .isEqualTo("ACCOUNT_DELETED");
        }
    }

    @Nested
    class RightToErasure {

        @Test
        void wipesEverythingThatIdentifiesThePerson() {
            User user = active();
            user.updateProfile("Marie Dupont", "Coureuse", "https://cdn/marie.png", LATER);
            user.recordPhysiology(new Physiology(
                    Optional.of(java.time.LocalDate.of(1998, 4, 12)), Optional.of(BiologicalSex.FEMALE),
                    OptionalDouble.of(58), OptionalDouble.of(165)), LATER);

            user.anonymize("a1b2c3d4", LATER);

            assertThat(user.handle().value()).isEqualTo("deleted-a1b2c3d4");
            assertThat(user.email().value()).doesNotContain("marie");
            assertThat(user.displayName()).isEqualTo("Compte supprimé");
            assertThat(user.bio()).isEmpty();
            assertThat(user.avatarUrl()).isEmpty();
            assertThat(user.physiology()).isEqualTo(Physiology.UNKNOWN);
            assertThat(user.accountScope()).isEqualTo(AudienceScope.PRIVATE);
            assertThat(user.status()).isEqualTo(AccountStatus.DELETED);
        }

        /** L'identifiant survit : les courses, likes et commentaires le référencent. */
        @Test
        void keepsTheTechnicalIdentityIntact() {
            User user = active();

            user.anonymize("a1b2c3d4", LATER);

            assertThat(user.id()).isEqualTo(ID);
            assertThat(user.registeredAt()).isEqualTo(REGISTERED);
        }

        @Test
        void refusesToDeleteTwice() {
            User user = active();
            user.anonymize("a1b2c3d4", LATER);

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> user.anonymize("e5f6a7b8", LATER))
                    .extracting(ConflictException::code)
                    .isEqualTo("ACCOUNT_DELETED");
        }

        @Test
        void refusesToDeleteWithoutASuffix() {
            User user = active();

            assertThatIllegalArgumentException().isThrownBy(() -> user.anonymize(null, LATER));
            assertThatIllegalArgumentException().isThrownBy(() -> user.anonymize("  ", LATER));
        }
    }

    @Test
    void rehydratesAPersistedStateWithoutReplayingItsHistory() {
        User user = User.rehydrate(ID, new Handle("marie"), new Email("marie@example.com"), "Marie",
                "https://cdn/x.png", "Coureuse", AudienceScope.FOLLOWERS, AccountStatus.SUSPENDED,
                Physiology.UNKNOWN, REGISTERED, LATER);

        assertThat(user.status()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(user.accountScope()).isEqualTo(AudienceScope.FOLLOWERS);
        assertThat(user.bio()).contains("Coureuse");
        assertThat(user.avatarUrl()).contains("https://cdn/x.png");
        assertThat(user.registeredAt()).isEqualTo(REGISTERED);
        assertThat(user.updatedAt()).isEqualTo(LATER);
    }

    @Nested
    class ModificationDate {

        /** À l'inscription, rien n'a encore été modifié : la seule date honnête est celle-là. */
        @Test
        void startsEqualToTheRegistrationDate() {
            assertThat(registered().updatedAt()).isEqualTo(REGISTERED);
        }

        @Test
        void everyMutationMovesIt() {
            User user = registered();
            Instant later = LATER.plusSeconds(3_600);

            user.verifyEmail(later);
            assertThat(user.updatedAt()).isEqualTo(later);

            user.updateProfile("Marie D.", null, null, later.plusSeconds(1));
            assertThat(user.updatedAt()).isEqualTo(later.plusSeconds(1));

            user.changeAvatar("https://cdn/y.png", later.plusSeconds(2));
            assertThat(user.updatedAt()).isEqualTo(later.plusSeconds(2));

            user.changeHandle(new Handle("marie.court"), later.plusSeconds(3));
            assertThat(user.updatedAt()).isEqualTo(later.plusSeconds(3));

            user.changeAccountScope(AudienceScope.PRIVATE, later.plusSeconds(4));
            assertThat(user.updatedAt()).isEqualTo(later.plusSeconds(4));

            user.recordPhysiology(Physiology.UNKNOWN, later.plusSeconds(5));
            assertThat(user.updatedAt()).isEqualTo(later.plusSeconds(5));

            user.anonymize("abcd", later.plusSeconds(6));
            assertThat(user.updatedAt()).isEqualTo(later.plusSeconds(6));
        }

        /** La date d'inscription, elle, ne bouge jamais. */
        @Test
        void leavesTheRegistrationDateAlone() {
            User user = active();

            user.changeHandle(new Handle("marie.court"), LATER);

            assertThat(user.registeredAt()).isEqualTo(REGISTERED);
        }

        @Test
        void refusesAMutationWithoutItsDate() {
            User user = active();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> user.changeHandle(new Handle("marie.court"), null));
        }
    }
}
