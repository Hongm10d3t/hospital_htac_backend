package com.htac_backend.modules.administration.controller;

import com.htac_backend.modules.administration.dto.AccountResponse;
import com.htac_backend.modules.administration.dto.AdminPasswordResetRequest;
import com.htac_backend.modules.administration.dto.ChangePasswordRequest;
import com.htac_backend.modules.administration.dto.UpdateOwnAccountRequest;
import com.htac_backend.modules.administration.service.PersonalAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PersonalAccountController {
    private final PersonalAccountService accounts;

    public PersonalAccountController(PersonalAccountService accounts) {
        this.accounts = accounts;
    }

    @PatchMapping("/api/v1/auth/me")
    public ResponseEntity<AccountResponse> updateOwn(@Valid @RequestBody UpdateOwnAccountRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.updateOwn(body));
    }

    @PostMapping("/api/v1/auth/change-password")
    public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordRequest body,
            HttpServletRequest request) {
        accounts.changePassword(body);
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/admin/accounts/{id}/password-reset")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
            @Valid @RequestBody AdminPasswordResetRequest body) {
        accounts.resetPassword(id, body);
        return ResponseEntity.noContent().build();
    }
}
