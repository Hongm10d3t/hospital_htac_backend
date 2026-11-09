package com.htac_backend.modules.administration.dto;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;

public record StaffAccountResponse(Long id, String username, String fullName, String phone,
                                   AccountRole role, boolean active) {
    public static StaffAccountResponse from(UserAccount account) {
        return new StaffAccountResponse(account.getId(), account.getUsername(), account.getFullName(),
                account.getPhone(), account.getRole(), account.isActive());
    }
}
