package com.runtrack.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;

class ViewerAuthenticationFilterTest {

    /** Un jeton refusé laisse passer en anonyme : c'est la règle d'autorisation qui tranche. */
    @Test
    void leavesTheRequestAnonymousWhenTheTokenIsRefused() throws Exception {
        var request = new MockHttpServletRequest("GET", "/race/v1/live");
        request.addHeader("Authorization", "Bearer perime");

        new ViewerAuthenticationFilter(token -> {
            throw new JwtException("expiré");
        }, Optional.empty()).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

}
