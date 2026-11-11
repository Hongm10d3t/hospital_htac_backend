package com.htac_backend.modules.procurement.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.pharmacy.service.PostedReceiptQuantityService;
import com.htac_backend.modules.pharmacy.service.ProcurementCatalogReferenceService;
import com.htac_backend.modules.procurement.dto.PurchaseOrderRequest;
import com.htac_backend.modules.procurement.dto.PurchaseRequestLineWrite;
import com.htac_backend.modules.procurement.dto.PurchaseRequestResponse;
import com.htac_backend.modules.procurement.dto.PurchaseRequestWrite;
import com.htac_backend.modules.procurement.entity.PurchaseRequest;
import com.htac_backend.modules.procurement.entity.PurchaseRequestItem;
import com.htac_backend.modules.procurement.repository.PurchaseRequestItemRepository;
import com.htac_backend.modules.procurement.repository.PurchaseRequestRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseRequestService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PENDING", "APPROVED", "REJECTED", "CANCELLED");
    private final PurchaseRequestRepository requests;
    private final PurchaseRequestItemRepository items;
    private final ProcurementCatalogReferenceService catalog;
    private final PostedReceiptQuantityService receipts;
    private final AccountRoleLookupService accounts;
    private final CurrentProcurementActor actor;

    public PurchaseRequestService(PurchaseRequestRepository requests, PurchaseRequestItemRepository items,
            ProcurementCatalogReferenceService catalog, PostedReceiptQuantityService receipts,
            AccountRoleLookupService accounts, CurrentProcurementActor actor) {
        this.requests = requests;
        this.items = items;
        this.catalog = catalog;
        this.receipts = receipts;
        this.accounts = accounts;
        this.actor = actor;
    }

    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    @Transactional(readOnly = true)
    public PageResponse<PurchaseRequestResponse> search(String status, Long supplierId, PageQuery page) {
        requireReader();
        if (status != null && !STATUSES.contains(status)) {
            throw new InvalidRequestException("Trạng thái đề xuất không hợp lệ");
        }
        if (supplierId != null && supplierId <= 0) {
            throw new InvalidRequestException("Nhà cung cấp không hợp lệ");
        }
        return PageResponse.from(requests.search(status, supplierId, page.toPageRequest())
                .map(this::response));
    }

    @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
    @Transactional(readOnly = true)
    public PurchaseRequestResponse get(Long id) {
        requireReader();
        return response(requests.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đề xuất nhập")));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseRequestResponse create(PurchaseRequestWrite body) {
        requireRole(AccountRole.PHARMACIST);
        validateBody(body);
        if (body.supplierId() != null) catalog.requireActiveSupplier(body.supplierId());
        validateMedicines(body.items());
        PurchaseRequest request = requests.saveAndFlush(new PurchaseRequest(code(),
                body.supplierId(), clean(body.note())));
        saveLines(request, body.items());
        return response(request);
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseRequestResponse update(Long id, PurchaseRequestWrite body) {
        requireRole(AccountRole.PHARMACIST);
        validateBody(body);
        PurchaseRequest request = lock(id);
        requireDraftOwner(request);
        if (body.supplierId() != null) catalog.requireActiveSupplier(body.supplierId());
        validateMedicines(body.items());
        items.deleteByRequestId(id);
        items.flush();
        request.revise(body.supplierId(), clean(body.note()));
        saveLines(request, body.items());
        return response(request);
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseRequestResponse submit(Long id) {
        requireRole(AccountRole.PHARMACIST);
        PurchaseRequest request = lock(id);
        if ("PENDING".equals(request.getStatus()) && actor.userId().equals(request.getCreatedBy())) {
            return response(request);
        }
        requireDraftOwner(request);
        if (request.getSupplierId() == null || request.getNote() == null
                || items.findByRequestIdOrderById(id).isEmpty()) {
            throw new InvalidRequestException("Đề xuất cần nhà cung cấp, căn cứ và ít nhất một thuốc");
        }
        catalog.requireActiveSupplier(request.getSupplierId());
        for (var item : items.findByRequestIdOrderById(id)) catalog.requireActiveMedicine(item.getMedicineId());
        request.submit();
        requests.flush();
        return response(request);
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseRequestResponse cancel(Long id) {
        requireRole(AccountRole.PHARMACIST);
        PurchaseRequest request = lock(id);
        requireDraftOwner(request);
        request.cancel();
        requests.flush();
        return response(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseRequestResponse approve(Long id, String reason) {
        return review(id, "APPROVED", reason);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseRequestResponse reject(Long id, String reason) {
        return review(id, "REJECTED", reason);
    }

    private PurchaseRequestResponse review(Long id, String decision, String reason) {
        requireRole(AccountRole.ADMIN);
        if (reason != null && reason.length() > 2000) throw new InvalidRequestException("Giải thích quá dài");
        if (decision.equals("REJECTED") && (reason == null || reason.isBlank())) {
            throw new InvalidRequestException("Từ chối cần lý do");
        }
        PurchaseRequest request = lock(id);
        if (decision.equals(request.getStatus())) return response(request);
        if (!"PENDING".equals(request.getStatus())) throw stateConflict();
        if (decision.equals("APPROVED")) catalog.requireActiveSupplier(request.getSupplierId());
        request.review(decision, actor.userId(), now(), clean(reason));
        requests.flush();
        return response(request);
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseRequestResponse order(Long id, PurchaseOrderRequest body) {
        requireRole(AccountRole.PHARMACIST);
        if (body == null || body.orderedAt() == null || body.expectedDeliveryDate() == null) {
            throw new InvalidRequestException("Cần ngày đặt và ngày dự kiến nhận");
        }
        PurchaseRequest request = lock(id);
        if (!"APPROVED".equals(request.getStatus()) || request.getClosedAt() != null) throw stateConflict();
        Instant orderedAt = body.orderedAt().toInstant().truncatedTo(ChronoUnit.MICROS);
        if (request.getOrderedAt() != null) {
            if (request.getOrderedAt().equals(orderedAt)
                    && request.getExpectedDeliveryDate().equals(body.expectedDeliveryDate())) return response(request);
            throw stateConflict();
        }
        if (orderedAt.isBefore(request.getReviewedAt()) || orderedAt.isAfter(now())
                || body.expectedDeliveryDate().isBefore(LocalDate.ofInstant(orderedAt, HOSPITAL_ZONE))) {
            throw new InvalidRequestException("Thời điểm đặt hoặc ngày dự kiến nhận không hợp lệ");
        }
        request.order(orderedAt, body.expectedDeliveryDate());
        requests.flush();
        return response(request);
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseRequestResponse close(Long id, String reason) {
        requireRole(AccountRole.PHARMACIST);
        if (reason != null && reason.length() > 2000) throw new InvalidRequestException("Giải thích quá dài");
        PurchaseRequest request = lock(id);
        if (request.getClosedAt() != null) return response(request);
        if (!"APPROVED".equals(request.getStatus())) throw stateConflict();
        if (receipts.hasDrafts(id)) {
            throw new BusinessException("PURCHASE_RECEIPT_DRAFT_EXISTS", "Cần xử lý phiếu nhập nháp trước khi kết thúc");
        }
        var received = receipts.receivedByMedicine(id);
        boolean incomplete = items.findByRequestIdOrderById(id).stream().anyMatch(item ->
                item.getQuantity().compareTo(received.getOrDefault(item.getMedicineId(), BigDecimal.ZERO)) > 0);
        if (incomplete && (reason == null || reason.isBlank())) {
            throw new InvalidRequestException("Kết thúc khi chưa nhận đủ cần lý do");
        }
        request.close(now(), clean(reason));
        requests.flush();
        return response(request);
    }

    private PurchaseRequest lock(Long id) {
        return requests.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đề xuất nhập"));
    }

    private void requireDraftOwner(PurchaseRequest request) {
        if (!"DRAFT".equals(request.getStatus())) throw stateConflict();
        if (!actor.userId().equals(request.getCreatedBy())) {
            throw new AccessDeniedException("Chỉ người lập được sửa đề xuất nháp");
        }
    }

    private void requireReader() {
        Long id = actor.userId();
        if (!accounts.hasActiveRole(id, AccountRole.PHARMACIST)
                && !accounts.hasActiveRole(id, AccountRole.ADMIN)) throw stateConflict();
    }

    private void requireRole(AccountRole role) {
        if (!accounts.hasActiveRole(actor.userId(), role)) {
            throw new BusinessException("STAFF_INACTIVE", "Tài khoản nhân viên không hoạt động");
        }
    }

    private void validateBody(PurchaseRequestWrite body) {
        if (body == null || body.items() == null || body.items().isEmpty() || body.items().size() > 100
                || (body.supplierId() != null && body.supplierId() <= 0)
                || (body.note() != null && body.note().length() > 2000)) {
            throw new InvalidRequestException("Đề xuất nhập không hợp lệ");
        }
        Set<Long> seen = new HashSet<>();
        for (PurchaseRequestLineWrite line : body.items()) {
            if (line == null || line.medicineId() == null || line.medicineId() <= 0
                    || line.quantity() == null || line.quantity().signum() <= 0
                    || line.quantity().scale() > 3
                    || line.quantity().precision() - line.quantity().scale() > 11
                    || (line.note() != null && line.note().length() > 2000)
                    || !seen.add(line.medicineId())) {
                throw new InvalidRequestException("Dòng đề xuất trùng hoặc không hợp lệ");
            }
        }
    }

    private void validateMedicines(List<PurchaseRequestLineWrite> lines) {
        for (var line : lines) catalog.requireActiveMedicine(line.medicineId());
    }

    private void saveLines(PurchaseRequest request, List<PurchaseRequestLineWrite> lines) {
        for (var line : lines) items.save(new PurchaseRequestItem(request,
                line.medicineId(), line.quantity(), clean(line.note())));
        items.flush();
    }

    private PurchaseRequestResponse response(PurchaseRequest request) {
        return PurchaseRequestResponse.from(request, items.findByRequestIdOrderById(request.getId()),
                receipts.receivedByMedicine(request.getId()));
    }

    private BusinessException stateConflict() {
        return new BusinessException("PURCHASE_STATE_CONFLICT", "Trạng thái đề xuất không cho phép thao tác");
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String code() { return "PR" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase(); }
    private Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
}
