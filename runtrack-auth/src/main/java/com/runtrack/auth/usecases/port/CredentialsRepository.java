package com.runtrack.auth.usecases.port;

import com.runtrack.auth.usecases.model.credential.Credentials;
import com.runtrack.shared.id.UserId;
import java.util.Optional;

public interface CredentialsRepository {

    Optional<Credentials> findByUserId(UserId userId);

    Credentials save(Credentials credentials);
}
