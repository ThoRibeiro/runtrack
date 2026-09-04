package com.runtrack.user.infrastructure.repository;

import com.runtrack.shared.access.AudienceScope;
import com.runtrack.shared.id.UserId;
import com.runtrack.user.usecases.model.profile.AccountStatus;
import com.runtrack.user.usecases.model.profile.BiologicalSex;
import com.runtrack.user.usecases.model.profile.Email;
import com.runtrack.user.usecases.model.profile.Handle;
import com.runtrack.user.usecases.model.profile.Physiology;
import com.runtrack.user.usecases.model.profile.User;
import com.runtrack.user.infrastructure.repository.entity.UserEntity;
import java.util.Optional;
import java.util.OptionalDouble;

/** Traduction entre l'agrégat et sa ligne. Écrite à la main : c'est vingt lignes lisibles. */
final class UserEntityMapper {

    private UserEntityMapper() {
    }

    static UserEntity toEntity(User user) {
        Physiology physiology = user.physiology();
        return new UserEntity(
                user.id().value(),
                user.handle().value(),
                user.email().value(),
                user.displayName(),
                user.avatarUrl().orElse(null),
                user.bio().orElse(null),
                user.accountScope().name(),
                user.status().name(),
                physiology.birthDate().orElse(null),
                physiology.sex().map(Enum::name).orElse(null),
                physiology.weightKilograms().isPresent() ? physiology.weightKilograms().getAsDouble() : null,
                physiology.heightCentimeters().isPresent() ? physiology.heightCentimeters().getAsDouble() : null,
                user.registeredAt(),
                user.updatedAt());
    }

    static User toDomain(UserEntity entity) {
        return User.rehydrate(
                new UserId(entity.getId()),
                new Handle(entity.getHandle()),
                new Email(entity.getEmail()),
                entity.getDisplayName(),
                entity.getAvatarUrl(),
                entity.getBio(),
                AudienceScope.valueOf(entity.getAccountScope()),
                AccountStatus.valueOf(entity.getStatus()),
                toPhysiology(entity),
                entity.getRegisteredAt(),
                entity.getUpdatedAt());
    }

    private static Physiology toPhysiology(UserEntity entity) {
        return new Physiology(
                Optional.ofNullable(entity.getBirthDate()),
                Optional.ofNullable(entity.getBiologicalSex()).map(BiologicalSex::valueOf),
                entity.getWeightKilograms() == null
                        ? OptionalDouble.empty() : OptionalDouble.of(entity.getWeightKilograms()),
                entity.getHeightCentimeters() == null
                        ? OptionalDouble.empty() : OptionalDouble.of(entity.getHeightCentimeters()));
    }
}
