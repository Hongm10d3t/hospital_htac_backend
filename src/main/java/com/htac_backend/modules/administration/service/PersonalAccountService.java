package com.htac_backend.modules.administration.service;

import com.htac_backend.modules.administration.dto.AccountResponse;
import com.htac_backend.modules.administration.dto.AdminPasswordResetRequest;
import com.htac_backend.modules.administration.dto.ChangePasswordRequest;
import com.htac_backend.modules.administration.dto.UpdateOwnAccountRequest;
import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.PasswordResetAudit;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.PasswordResetAuditRepository;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalAccountService {
    private final UserAccountRepository accounts;
    private final PasswordResetAuditRepository resetAudits;
    private final PasswordEncoder encoder;

    public PersonalAccountService(UserAccountRepository accounts, PasswordResetAuditRepository resetAudits,
            PasswordEncoder encoder) {
        this.accounts = accounts;
        this.resetAudits = resetAudits;
        this.encoder = encoder;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public AccountResponse updateOwn(UpdateOwnAccountRequest body) {
        UserAccount account = ownAccount();
        if (body.fullName() == null && body.phone() == null) {
            throw new InvalidRequestException("Không có thông tin cần cập nhật");
        }
        String fullName = body.fullName() == null ? account.getFullName() : body.fullName().trim();
        String phone = body.phone() == null ? account.getPhone() : body.phone().trim();
        if (fullName.isBlank() || phone.isBlank()) {
            throw new InvalidRequestException("Tên hoặc số điện thoại không hợp lệ");
        }
        account.update(account.getUsername(), fullName, phone);
        accounts.flush();
        return AccountResponse.from(account);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void changePassword(ChangePasswordRequest body) {
        UserAccount account = ownAccount();
        if (body.currentPassword() == null || body.currentPassword().isBlank()
                || !encoder.matches(body.currentPassword(), account.getPasswordHash())) {
            throw new InvalidRequestException("Mật khẩu hiện tại không đúng");
        }
        requireNewPassword(body.newPassword(), account);
        account.changePasswordHash(encoder.encode(body.newPassword()));
        accounts.flush();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void resetPassword(Long targetId, AdminPasswordResetRequest body) {
        Long adminId = principal().userId();
        UserAccount target = accounts.findByIdForUpdate(targetId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản"));
        if (target.getRole() == AccountRole.ADMIN) {
            throw new BusinessException("ADMIN_RESET_FORBIDDEN", "Không đặt lại mật khẩu ADMIN qua API này");
        }
        if (body.verifiedVia() == null) {
            throw new InvalidRequestException("Cần ghi phương thức xác minh");
        }
        requireNewPassword(body.newPassword(), target);
        target.changePasswordHash(encoder.encode(body.newPassword()));
        accounts.flush();
        resetAudits.saveAndFlush(new PasswordResetAudit(targetId, adminId, body.verifiedVia()));
    }

    private UserAccount ownAccount() {
        return accounts.findByIdForUpdate(principal().userId())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Cần đăng nhập"));
    }

    private AccountPrincipal principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("Cần đăng nhập");
        }
        return principal;
    }

    private void requireNewPassword(String password, UserAccount account) {
        if (password == null || password.isBlank() || password.length() < 6 || password.length() > 128) {
            throw new InvalidRequestException("Mật khẩu mới phải có từ 6 đến 128 ký tự");
        }
        if (encoder.matches(password, account.getPasswordHash())) {
            throw new InvalidRequestException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
    }
}
