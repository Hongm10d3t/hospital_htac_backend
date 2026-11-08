package com.htac_backend.modules.administration.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BootstrapAdminService {
    private final UserAccountRepository accounts;
    private final PasswordEncoder encoder;

    public BootstrapAdminService(UserAccountRepository accounts, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.encoder = encoder;
    }

    @Transactional
    public boolean createIfMissing(String username, String password, String fullName, String phone) {
        if (accounts.existsByRole(AccountRole.ADMIN)) {
            return false;
        }
        requireLength("username", username, 1, 50);
        requireLength("password", password, 6, 128);
        requireLength("fullName", fullName, 1, 150);
        requireLength("phone", phone, 1, 20);
        if (username.isBlank() || fullName.isBlank() || phone.isBlank()) {
            throw new IllegalArgumentException("Thiếu thông tin khởi tạo ADMIN");
        }
        accounts.saveAndFlush(new UserAccount(username, encoder.encode(password), fullName, phone, AccountRole.ADMIN));
        return true;
    }

    private static void requireLength(String field, String value, int min, int max) {
        if (value == null || value.length() < min || value.length() > max) {
            throw new IllegalArgumentException("Thông tin khởi tạo ADMIN không hợp lệ: " + field);
        }
    }
}
