package com.runtrack.auth.internal.application.port;

import com.runtrack.auth.internal.domain.credential.Credentials;
import com.runtrack.shared.id.UserId;
import java.util.Optional;

public interface CredentialsRepository {

    Optional<Credentials> findByUserId(UserId userId);

    Credentials save(Credentials credentials);
}
