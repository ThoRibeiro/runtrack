package com.runtrack.auth.internal.infra.jpa;

import com.runtrack.auth.internal.application.port.CredentialsRepository;
import com.runtrack.auth.internal.domain.credential.Credentials;
import com.runtrack.auth.internal.domain.credential.PasswordHash;
import com.runtrack.auth.internal.infra.jpa.entity.CredentialsEntity;
import com.runtrack.shared.id.UserId;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaCredentialsRepository implements CredentialsRepository {

    private final SpringDataCredentialsRepository entities;

    JpaCredentialsRepository(SpringDataCredentialsRepository entities) {
        this.entities = entities;
    }

    @Override
    public Optional<Credentials> findByUserId(UserId userId) {
        return entities.findById(userId.value()).map(entity -> Credentials.rehydrate(
                new UserId(entity.getUserId()),
                new PasswordHash(entity.getPasswordHash()),
                entity.getPasswordChangedAt()));
    }

    @Override
    public Credentials save(Credentials credentials) {
        var incoming = new CredentialsEntity(
                credentials.userId().value(),
                credentials.passwordHash().value(),
                credentials.passwordChangedAt());
        entities.save(entities.findById(credentials.userId().value())
                .map(existing -> {
                    existing.refreshFrom(incoming);
                    return existing;
                })
                .orElse(incoming));
        return credentials;
    }
}
