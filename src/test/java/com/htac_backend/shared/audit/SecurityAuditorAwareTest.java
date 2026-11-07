package com.htac_backend.shared.audit;

import com.htac_backend.security.AuthenticatedActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityAuditorAwareTest {
    private final SecurityAuditorAware auditor = new SecurityAuditorAware();

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void systemWriteHasNoUserAuditor() {
        assertTrue(auditor.getCurrentAuditor().isEmpty());
    }

    @Test
    void authenticatedPrincipalSuppliesAuditUserId() {
        AuthenticatedActor actor = () -> 42L;
        var authentication = new UsernamePasswordAuthenticationToken(actor, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        assertEquals(42L, auditor.getCurrentAuditor().orElseThrow());
    }
}
