package com.htac_backend.modules.administration.dto;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;

public record AccountResponse(Long id, String username, String fullName, String phone, AccountRole role) {
    public static AccountResponse from(UserAccount account) {
        return new AccountResponse(account.getId(), account.getUsername(), account.getFullName(),
                account.getPhone(), account.getRole());
    }

    @Override
    public String toString() {
        return "AccountResponse[redacted]";
    }
}
