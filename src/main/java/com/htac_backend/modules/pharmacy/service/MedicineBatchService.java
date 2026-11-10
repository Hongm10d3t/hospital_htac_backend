package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.pharmacy.dto.MedicineBatchResponse;
import com.htac_backend.modules.pharmacy.dto.MedicineBatchWriteRequest;
import com.htac_backend.modules.pharmacy.entity.Medicine;
import com.htac_backend.modules.pharmacy.entity.MedicineBatch;
import com.htac_backend.modules.pharmacy.repository.MedicineBatchRepository;
import com.htac_backend.modules.pharmacy.repository.MedicineRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicineBatchService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final MedicineBatchRepository batches;
    private final MedicineRepository medicines;
    private final CurrentPharmacistActor actor;
    private final AccountRoleLookupService accounts;
    private final JdbcTemplate jdbc;

    public MedicineBatchService(MedicineBatchRepository batches, MedicineRepository medicines,
            CurrentPharmacistActor actor, AccountRoleLookupService accounts, JdbcTemplate jdbc) {
        this.batches = batches;
        this.medicines = medicines;
        this.actor = actor;
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(readOnly = true)
    public PageResponse<MedicineBatchResponse> search(Long medicineId, String status,
            String term, PageQuery page) {
        requirePharmacist();
        if (status != null && !status.equals("ACTIVE") && !status.equals("BLOCKED")) {
            throw new InvalidRequestException("Trạng thái lô không hợp lệ");
        }
        return PageResponse.from(batches.search(medicineId, status,
                term == null || term.isBlank() ? null : term.trim(), page.toPageRequest())
                .map(MedicineBatchResponse::from));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(readOnly = true)
    public MedicineBatchResponse get(Long id) {
        requirePharmacist();
        return MedicineBatchResponse.from(batches.findDetail(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lô thuốc")));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MedicineBatchResponse create(MedicineBatchWriteRequest body) {
        requirePharmacist();
        validate(body);
        Medicine medicine = medicines.findByIdForUpdate(body.medicineId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thuốc"));
        String batchNumber = body.batchNumber().trim();
        if (batches.existsByMedicineIdAndBatchNumber(medicine.getId(), batchNumber)) {
            throw duplicate();
        }
        MedicineBatch batch = new MedicineBatch(medicine, batchNumber, body.expiryDate());
        batch.update(batchNumber, body.expiryDate(), body.status(), reason(body));
        return MedicineBatchResponse.from(batches.saveAndFlush(batch));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MedicineBatchResponse update(Long id, MedicineBatchWriteRequest body) {
        requirePharmacist();
        validate(body);
        MedicineBatch batch = batches.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lô thuốc"));
        if (!batch.getMedicine().getId().equals(body.medicineId())) {
            throw new BusinessException("BATCH_MEDICINE_LOCKED", "Không thể chuyển lô sang thuốc khác; tạo lô mới");
        }
        String batchNumber = body.batchNumber().trim();
        if (batches.existsByMedicineIdAndBatchNumberAndIdNot(body.medicineId(), batchNumber, id)) {
            throw duplicate();
        }
        boolean identityChanged = !batch.getBatchNumber().equals(batchNumber)
                || !batch.getExpiryDate().equals(body.expiryDate());
        if (identityChanged && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM stock_document_item WHERE batch_id = ?)", Boolean.class, id))) {
            throw new BusinessException("BATCH_IDENTITY_LOCKED",
                    "Lô đã có phiếu kho; không thể đổi số lô hoặc hạn dùng");
        }
        batch.update(batchNumber, body.expiryDate(), body.status(), reason(body));
        batches.flush();
        return MedicineBatchResponse.from(batch);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MedicineBatch requireDispensable(Long id, LocalDate businessDate) {
        MedicineBatch batch = batches.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lô thuốc"));
        if (!isDispensable(batch, businessDate)) {
            throw new BusinessException("BATCH_NOT_DISPENSABLE", "Lô bị khóa, hết hạn hoặc thuốc ngừng hoạt động");
        }
        return batch;
    }

    public boolean isDispensable(MedicineBatch batch, LocalDate businessDate) {
        return batch.getStatus().equals("ACTIVE") && batch.getMedicine().isActive()
                && batch.getExpiryDate().isAfter(businessDate);
    }

    public LocalDate businessDate() {
        return LocalDate.now(HOSPITAL_ZONE);
    }

    private void validate(MedicineBatchWriteRequest body) {
        if (body == null || body.medicineId() == null || body.medicineId() <= 0
                || body.batchNumber() == null || body.batchNumber().isBlank()
                || body.batchNumber().trim().length() > 60 || body.expiryDate() == null
                || (!"ACTIVE".equals(body.status()) && !"BLOCKED".equals(body.status()))
                || ("BLOCKED".equals(body.status()) && (body.blockReason() == null
                        || body.blockReason().isBlank()))
                || (body.blockReason() != null && body.blockReason().length() > 2000)) {
            throw new InvalidRequestException("Thông tin lô thuốc không hợp lệ");
        }
    }

    private String reason(MedicineBatchWriteRequest body) {
        return "BLOCKED".equals(body.status()) ? body.blockReason().trim() : null;
    }

    private BusinessException duplicate() {
        return new BusinessException("BATCH_NUMBER_TAKEN", "Số lô đã tồn tại cho thuốc này");
    }

    private void requirePharmacist() {
        if (!accounts.hasActiveRole(actor.userId(), AccountRole.PHARMACIST)) {
            throw new BusinessException("PHARMACIST_INACTIVE", "Tài khoản dược sĩ không hoạt động");
        }
    }
}
