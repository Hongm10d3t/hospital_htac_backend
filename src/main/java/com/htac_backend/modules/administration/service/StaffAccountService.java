package com.htac_backend.modules.administration.service;

import com.htac_backend.modules.administration.dto.CreateStaffAccountRequest;
import com.htac_backend.modules.administration.dto.StaffAccountResponse;
import com.htac_backend.modules.administration.dto.UpdateStaffAccountRequest;
import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.clinicdirectory.service.DoctorProfileCreationService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class StaffAccountService {
    private static final Set<AccountRole> STAFF_ROLES = Set.of(
            AccountRole.RECEPTIONIST, AccountRole.DOCTOR, AccountRole.PHARMACIST);

    private final UserAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final DoctorProfileCreationService doctorProfiles;
    private final AccountRoleHistoryService history;

    public StaffAccountService(UserAccountRepository accounts, PasswordEncoder encoder,
            DoctorProfileCreationService doctorProfiles, AccountRoleHistoryService history) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.doctorProfiles = doctorProfiles;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public PageResponse<StaffAccountResponse> list(PageQuery page) {
        return PageResponse.from(accounts.findByRoleIn(STAFF_ROLES, page.toPageRequest())
                .map(StaffAccountResponse::from));
    }

    @Transactional(readOnly = true)
    public StaffAccountResponse get(Long id) {
        return StaffAccountResponse.from(staff(id));
    }

    @Transactional
    public StaffAccountResponse create(CreateStaffAccountRequest body) {
        requireStaffRole(body.role());
        requireSpecialtyShape(body.role(), body.specialtyId());
        if (body.password() == null || body.password().isBlank()
                || body.password().length() < 6 || body.password().length() > 128) {
            throw new InvalidRequestException("Mật khẩu phải có từ 6 đến 128 ký tự");
        }
        if (accounts.existsByUsername(body.username())) {
            throw new BusinessException("USERNAME_TAKEN", "Tên đăng nhập đã được sử dụng");
        }
        UserAccount account = accounts.saveAndFlush(new UserAccount(body.username(), encoder.encode(body.password()),
                body.fullName().trim(), body.phone().trim(), body.role()));
        if (body.role() == AccountRole.DOCTOR) {
            doctorProfiles.create(account.getId(), body.specialtyId());
        }
        return StaffAccountResponse.from(account);
    }

    @Transactional
    public StaffAccountResponse update(Long id, UpdateStaffAccountRequest body) {
        UserAccount account = staffForUpdate(id);
        if (body.username() == null && body.fullName() == null && body.phone() == null && body.role() == null) {
            throw new InvalidRequestException("Không có thông tin cần cập nhật");
        }
        String username = body.username() == null ? account.getUsername() : body.username();
        String fullName = body.fullName() == null ? account.getFullName() : body.fullName().trim();
        String phone = body.phone() == null ? account.getPhone() : body.phone().trim();
        if (fullName.isBlank() || phone.isBlank()) {
            throw new InvalidRequestException("Tên hoặc số điện thoại không hợp lệ");
        }
        if (!username.equals(account.getUsername()) && accounts.existsByUsername(username)) {
            throw new BusinessException("USERNAME_TAKEN", "Tên đăng nhập đã được sử dụng");
        }
        AccountRole newRole = body.role() == null ? account.getRole() : body.role();
        requireStaffRole(newRole);
        boolean roleChanged = newRole != account.getRole();
        if (roleChanged && history.hasLinkedData(account.getId())) {
            throw new BusinessException("ROLE_LOCKED", "Tài khoản đã có dữ liệu liên kết, không thể đổi vai trò");
        }
        if (roleChanged) {
            requireSpecialtyShape(newRole, body.specialtyId());
            account.changeRole(newRole);
        } else if (body.specialtyId() != null) {
            throw new InvalidRequestException("Chuyên khoa chỉ được gửi khi chuyển sang vai trò bác sĩ");
        }
        account.update(username, fullName, phone);
        accounts.flush();
        if (roleChanged && newRole == AccountRole.DOCTOR) {
            doctorProfiles.create(account.getId(), body.specialtyId());
        }
        return StaffAccountResponse.from(account);
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        UserAccount account = staffForUpdate(id);
        account.setActive(active);
        accounts.flush();
    }

    private UserAccount staff(Long id) {
        UserAccount account = accounts.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản nhân viên"));
        requireExistingStaff(account);
        return account;
    }

    private UserAccount staffForUpdate(Long id) {
        UserAccount account = accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản nhân viên"));
        requireExistingStaff(account);
        return account;
    }

    private void requireExistingStaff(UserAccount account) {
        if (!STAFF_ROLES.contains(account.getRole())) {
            throw new NotFoundException("Không tìm thấy tài khoản nhân viên");
        }
    }

    private void requireStaffRole(AccountRole role) {
        if (!STAFF_ROLES.contains(role)) {
            throw new InvalidRequestException("Vai trò nhân viên không hợp lệ");
        }
    }

    private void requireSpecialtyShape(AccountRole role, Long specialtyId) {
        if (role == AccountRole.DOCTOR && (specialtyId == null || specialtyId < 1)) {
            throw new InvalidRequestException("Bác sĩ cần chuyên khoa hợp lệ");
        }
        if (role != AccountRole.DOCTOR && specialtyId != null) {
            throw new InvalidRequestException("Chỉ bác sĩ được gắn chuyên khoa");
        }
    }
}
