package com.htac_backend.modules.scheduling.service;

import com.htac_backend.security.AuthenticatedActor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentSchedulingActor {
    public Long userId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedActor actor)) {
            throw new AuthenticationCredentialsNotFoundException("Cần đăng nhập");
        }
        return actor.userId();
    }
}
