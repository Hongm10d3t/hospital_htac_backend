package com.htac_backend.modules.administration.service;

import com.htac_backend.modules.administration.dto.AccountResponse;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountSessionService {
    private final UserAccountRepository accounts;

    public AccountSessionService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public AccountResponse current(AccountPrincipal principal) {
        return accounts.findById(principal.userId()).map(AccountResponse::from)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Cần đăng nhập"));
    }
}
