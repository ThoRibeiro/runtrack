package com.runtrack.user.infrastructure.endpoint;

import com.runtrack.user.usecases.model.profile.BiologicalSex;
import com.runtrack.user.usecases.model.profile.Physiology;
import com.runtrack.user.usecases.model.profile.User;
import com.runtrack.user.infrastructure.dto.ProfileDtos;
import java.util.Optional;
import java.util.OptionalDouble;

/** Agrégat vers DTO, à la main. Le sens inverse passe par les objets valeur du domaine. */
final class ProfileMapper {

    private ProfileMapper() {
    }

    static ProfileDtos.MyProfile toMyProfile(User user) {
        return new ProfileDtos.MyProfile(
                user.id().toString(),
                user.handle().value(),
                user.email().value(),
                user.displayName(),
                user.avatarUrl().orElse(null),
                user.bio().orElse(null),
                user.accountScope().name(),
                user.status().name(),
                user.registeredAt(),
                user.updatedAt());
    }

    static ProfileDtos.PublicProfile toPublicProfile(User user) {
        return new ProfileDtos.PublicProfile(
                user.id().toString(),
                user.handle().value(),
                user.displayName(),
                user.avatarUrl().orElse(null),
                user.bio().orElse(null),
                user.accountScope().name());
    }

    static ProfileDtos.PhysiologyPayload toPayload(Physiology physiology) {
        return new ProfileDtos.PhysiologyPayload(
                physiology.birthDate().orElse(null),
                physiology.sex().map(Enum::name).orElse(null),
                physiology.weightKilograms().isPresent() ? physiology.weightKilograms().getAsDouble() : null,
                physiology.heightCentimeters().isPresent() ? physiology.heightCentimeters().getAsDouble() : null);
    }

    static Physiology toPhysiology(ProfileDtos.PhysiologyPayload payload) {
        return new Physiology(
                Optional.ofNullable(payload.birthDate()),
                Optional.ofNullable(payload.biologicalSex()).map(BiologicalSex::valueOf),
                payload.weightKilograms() == null ? OptionalDouble.empty()
                        : OptionalDouble.of(payload.weightKilograms()),
                payload.heightCentimeters() == null ? OptionalDouble.empty()
                        : OptionalDouble.of(payload.heightCentimeters()));
    }
}
