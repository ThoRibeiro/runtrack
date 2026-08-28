package com.runtrack.auth.internal.application.port;

import com.runtrack.auth.internal.domain.token.SingleUseToken;
import com.runtrack.auth.internal.domain.token.TokenPurpose;
import com.runtrack.shared.id.UserId;
import java.util.Optional;

public interface SingleUseTokenRepository {

    Optional<SingleUseToken> findByHash(String tokenHash);

    SingleUseToken save(SingleUseToken token);

    /** Invalide les liens encore en circulation quand on en émet un nouveau. */
    void consumeAllOf(UserId userId, TokenPurpose purpose);
}
