package com.htac_backend.modules.administration.service;

import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Returns only names approved for public doctor profile display. */
@Service
public class AccountPublicIdentityService {
    private final UserAccountRepository accounts;

    public AccountPublicIdentityService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Map<Long, String> fullNamesFor(Collection<Long> accountIds) {
        return accounts.findAllById(accountIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getFullName));
    }
}
