package com.htac_backend.modules.administration.controller;

import com.htac_backend.modules.administration.dto.AccountResponse;
import com.htac_backend.modules.administration.dto.CsrfResponse;
import com.htac_backend.modules.administration.dto.LoginRequest;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.administration.service.AccountSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final AccountSessionService sessions;

    public AuthController(AuthenticationManager authenticationManager, SessionAuthenticationStrategy sessionStrategy,
                          SecurityContextRepository securityContextRepository, AccountSessionService sessions) {
        this.authenticationManager = authenticationManager;
        this.sessionStrategy = sessionStrategy;
        this.securityContextRepository = securityContextRepository;
        this.sessions = sessions;
    }

    @GetMapping("/api/v1/public/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @PostMapping("/api/v1/public/auth/login")
    public ResponseEntity<AccountResponse> login(@Valid @RequestBody LoginRequest body,
                                                  HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        sessionStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(sessions.current((AccountPrincipal) authentication.getPrincipal()));
    }

    @GetMapping("/api/v1/auth/me")
    public ResponseEntity<AccountResponse> me(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(sessions.current((AccountPrincipal) authentication.getPrincipal()));
    }

}
