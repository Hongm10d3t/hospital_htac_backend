package com.htac_backend.security;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class ActiveAccountFilter extends OncePerRequestFilter {
    private final UserAccountRepository accounts;
    private final SecurityErrorWriter errors;

    public ActiveAccountFilter(UserAccountRepository accounts, SecurityErrorWriter errors) {
        this.accounts = accounts;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            boolean valid = accounts.findById(principal.userId())
                    .map(account -> account.isActive() && account.getRole() == principal.role()
                            && account.getUsername().equals(principal.getUsername())
                            && account.getCredentialVersion() == principal.credentialVersion())
                    .orElse(false);
            if (!valid) {
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) {
                    request.getSession(false).invalidate();
                }
                errors.write(request, response, 401, "UNAUTHENTICATED", "Cần đăng nhập");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
