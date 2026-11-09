package com.htac_backend.modules.administration.service;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.shared.api.NotFoundException;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import com.htac_backend.modules.administration.entity.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Only the phone needed for receptionist confirmation, never credentials. */
@Service
public class AccountContactLookupService {
    private final UserAccountRepository accounts;

    public AccountContactLookupService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public String phoneFor(Long accountId) {
        return accounts.findById(accountId).map(account -> account.getPhone())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản liên hệ"));
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Map<Long, String> phonesFor(Collection<Long> accountIds) {
        return accounts.findAllById(accountIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getPhone));
    }
}
