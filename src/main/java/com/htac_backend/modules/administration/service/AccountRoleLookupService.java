package com.htac_backend.modules.administration.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountRoleLookupService {
    private final UserAccountRepository accounts;

    public AccountRoleLookupService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public boolean hasRole(Long accountId, AccountRole role) {
        return accounts.findById(accountId).map(account -> account.getRole() == role).orElse(false);
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public boolean hasActiveRole(Long accountId, AccountRole role) {
        return accounts.findById(accountId)
                .map(account -> account.isActive() && account.getRole() == role).orElse(false);
    }
}
