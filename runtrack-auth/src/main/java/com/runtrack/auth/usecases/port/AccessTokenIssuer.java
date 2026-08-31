package com.runtrack.auth.usecases.port;

import com.runtrack.shared.id.UserId;
import java.time.Duration;

/** L'émission des jetons d'accès. La signature RS256 et les clés vivent dans {@code infra}. */
public interface AccessTokenIssuer {

    String issueFor(UserId userId);

    Duration lifetime();
}
