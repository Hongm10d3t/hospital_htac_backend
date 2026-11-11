package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.outpatient.dto.IssuedPrescriptionSnapshot;
import com.htac_backend.modules.outpatient.dto.PrescriptionLineResponse;
import com.htac_backend.modules.outpatient.service.IssuedPrescriptionService;
import com.htac_backend.modules.outpatient.service.PrescriptionDispensingStateService;
import com.htac_backend.modules.pharmacy.dto.StockDocumentResponse;
import com.htac_backend.modules.pharmacy.entity.StockDocument;
import com.htac_backend.modules.pharmacy.entity.StockDocumentItem;
import com.htac_backend.modules.pharmacy.repository.MedicineBatchRepository;
import com.htac_backend.modules.pharmacy.repository.StockDocumentItemRepository;
import com.htac_backend.modules.pharmacy.repository.StockDocumentRepository;
import com.htac_backend.modules.pharmacy.repository.SupplierRepository;
import com.htac_backend.modules.procurement.service.PurchaseReceiptValidationService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockDocumentService {
    private static final Set<String> ENABLED_TYPES = Set.of("OPENING", "RECEIPT", "TRANSFER",
            "DISPENSE", "PATIENT_RETURN", "SUPPLIER_RETURN", "DISPOSAL", "ADJUSTMENT");
    private final StockDocumentRepository documents;
    private final StockDocumentItemRepository items;
    private final MedicineBatchRepository batches;
    private final StockBalanceService balances;
    private final SupplierRepository suppliers;
    private final MedicineBatchService batchRules;
    private final IssuedPrescriptionService prescriptions;
    private final DispensedQuantityService dispensed;
    private final PrescriptionDispensingStateService prescriptionState;
    private final PatientReturnReferenceService returnReferences;
    private final CurrentPharmacistActor actor;
    private final AccountRoleLookupService accounts;
    private final PurchaseReceiptValidationService procurement;

    public StockDocumentService(StockDocumentRepository documents,
            StockDocumentItemRepository items, MedicineBatchRepository batches,
            StockBalanceService balances, SupplierRepository suppliers,
            MedicineBatchService batchRules, IssuedPrescriptionService prescriptions,
            DispensedQuantityService dispensed, PrescriptionDispensingStateService prescriptionState,
            PatientReturnReferenceService returnReferences,
            CurrentPharmacistActor actor,
            AccountRoleLookupService accounts, PurchaseReceiptValidationService procurement) {
        this.documents = documents;
        this.items = items;
        this.batches = batches;
        this.balances = balances;
        this.suppliers = suppliers;
        this.batchRules = batchRules;
        this.prescriptions = prescriptions;
        this.dispensed = dispensed;
        this.prescriptionState = prescriptionState;
        this.returnReferences = returnReferences;
        this.actor = actor;
        this.accounts = accounts;
        this.procurement = procurement;
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(readOnly = true)
    public StockDocumentResponse get(Long id) {
        requirePharmacist();
        StockDocument document = documents.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu kho"));
        return StockDocumentResponse.from(document, items.findDetailsByDocumentId(id));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockDocumentResponse post(Long id) {
        requirePharmacist();
        StockDocument document = documents.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu kho"));
        if (document.getStatus().equals("POSTED")) {
            return StockDocumentResponse.from(document, items.findDetailsByDocumentId(id));
        }
        if (!document.getStatus().equals("DRAFT")) {
            throw new BusinessException("STOCK_DOCUMENT_FINAL", "Phiếu đã hủy không thể ghi sổ");
        }
        IssuedPrescriptionSnapshot prescription = null;
        if (document.getType().equals("DISPENSE")
                || document.getType().equals("PATIENT_RETURN")) {
            if (document.getPrescriptionId() == null) {
                throw new InvalidRequestException("Phiếu cấp thuốc thiếu đơn");
            }
            prescription = prescriptions.lockForDispensing(document.getPrescriptionId());
        }
        if (document.getType().equals("RECEIPT")) {
            var supplier = suppliers.findByIdForUpdate(document.getSupplierId())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy nhà cung cấp"));
            if (!supplier.isActive()) {
                throw new BusinessException("SUPPLIER_INACTIVE", "Nhà cung cấp đã ngừng hoạt động");
            }
        }
        // One batch row coordinates all writers, including the first document for an empty balance.
        items.findBatchIdsByDocumentId(id).forEach(batchId -> batches.findByIdForUpdate(batchId)
                        .orElseThrow(() -> new NotFoundException("Không tìm thấy lô thuốc")));
        List<StockDocumentItem> lines = items.findDetailsByDocumentId(id);
        validate(document, lines);
        if (document.getType().equals("RECEIPT") && document.getPurchaseRequestId() != null) {
            Map<Long, BigDecimal> incoming = new HashMap<>();
            lines.forEach(line -> incoming.merge(line.getBatch().getMedicine().getId(),
                    line.getQuantity(), BigDecimal::add));
            procurement.check(document.getPurchaseRequestId(), document.getSupplierId(), incoming);
        }
        if (prescription != null) {
            if (document.getType().equals("DISPENSE")) {
                validateDispense(document, lines, prescription);
            } else {
                validatePatientReturn(document, lines);
            }
        }
        if (document.getType().equals("ADJUSTMENT")) {
            for (StockDocumentItem line : lines) {
                BigDecimal actual = balances.balance(new StockPosition(line.getBatch().getId(),
                        document.getStockLocation(), document.getStockBucket()));
                if (actual.compareTo(line.getBookQuantity()) != 0) {
                    throw new BusinessException("STOCK_COUNT_STALE",
                            "Tồn sổ đã thay đổi; đọc lại tồn và kiểm đếm lại");
                }
            }
        }
        if (document.getType().equals("TRANSFER")) {
            boolean release = "QUARANTINE".equals(document.getStockBucket())
                    && "AVAILABLE".equals(document.getDestinationBucket());
            boolean quarantine = "AVAILABLE".equals(document.getStockBucket())
                    && "QUARANTINE".equals(document.getDestinationBucket());
            if ((release && (document.getNote() == null
                    || !document.getNote().startsWith(TransferStockService.RELEASE_PREFIX)
                    || document.getNote().length() <= TransferStockService.RELEASE_PREFIX.length()))
                    || (quarantine && (document.getNote() == null || document.getNote().isBlank()))) {
                throw new InvalidRequestException("Chuyển nhóm tồn cần lý do và xác nhận xử lý");
            }
            if (release) {
                var today = batchRules.businessDate();
                for (StockDocumentItem line : lines) {
                    if (!batchRules.isDispensable(line.getBatch(), today)) {
                        throw new BusinessException("BATCH_NOT_RELEASABLE",
                                "Lô bị khóa, hết hạn hoặc thuốc ngừng hoạt động");
                    }
                }
            }
        }
        Map<StockPosition, BigDecimal> deltas = new HashMap<>();
        for (StockDocumentItem line : lines) {
            StockPosition source = new StockPosition(line.getBatch().getId(),
                    document.getStockLocation(), document.getStockBucket());
            BigDecimal quantity = line.getQuantity();
            if (document.getType().equals("TRANSFER")) {
                add(deltas, source, quantity.negate());
                add(deltas, new StockPosition(line.getBatch().getId(),
                        document.getDestinationLocation(), document.getDestinationBucket()), quantity);
            } else if (Set.of("DISPENSE", "SUPPLIER_RETURN", "DISPOSAL")
                    .contains(document.getType())) {
                add(deltas, source, quantity.negate());
            } else {
                add(deltas, source, quantity);
            }
        }
        for (var entry : deltas.entrySet()) {
            if (balances.balance(entry.getKey()).add(entry.getValue()).signum() < 0) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                        "Tồn tại lô, điểm và nhóm không đủ để ghi sổ");
            }
        }
        Instant postedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        document.post(actor.userId(), postedAt);
        documents.flush();
        if (prescription != null && document.getType().equals("DISPENSE")) {
            Map<Long, BigDecimal> remaining = new HashMap<>();
            for (var line : prescription.items()) {
                remaining.put(line.id(), line.quantity().subtract(dispensed.postedQuantity(line.id())));
            }
            prescriptionState.reconcileAfterDispense(prescription.id(), remaining, postedAt);
        }
        return StockDocumentResponse.from(document, lines);
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockDocumentResponse cancel(Long id, String reason) {
        requirePharmacist();
        if (reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new InvalidRequestException("Cần lý do hủy phiếu");
        }
        StockDocument document = documents.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiếu kho"));
        if (!document.getStatus().equals("DRAFT")) {
            throw new BusinessException("STOCK_DOCUMENT_FINAL", "Chỉ phiếu nháp được hủy");
        }
        document.cancel(reason.trim());
        documents.flush();
        return StockDocumentResponse.from(document, items.findDetailsByDocumentId(id));
    }

    private void validate(StockDocument document, List<StockDocumentItem> lines) {
        if (!ENABLED_TYPES.contains(document.getType())) {
            throw new BusinessException("STOCK_TYPE_UNAVAILABLE", "Loại phiếu chưa được triển khai");
        }
        if (lines.isEmpty() || !location(document.getStockLocation()) || !bucket(document.getStockBucket())) {
            throw new InvalidRequestException("Phiếu kho thiếu dòng hoặc vị trí hợp lệ");
        }
        boolean transfer = document.getType().equals("TRANSFER");
        if (transfer) {
            if (!location(document.getDestinationLocation()) || !bucket(document.getDestinationBucket())
                    || (document.getStockLocation().equals(document.getDestinationLocation())
                    && document.getStockBucket().equals(document.getDestinationBucket()))) {
                throw new InvalidRequestException("Nguồn và đích chuyển kho không hợp lệ");
            }
        } else if (document.getDestinationLocation() != null || document.getDestinationBucket() != null) {
            throw new InvalidRequestException("Phiếu này không có vị trí đích");
        }
        boolean supplierType = document.getType().equals("RECEIPT")
                || document.getType().equals("SUPPLIER_RETURN");
        if (supplierType && document.getSupplierId() == null) {
            throw new InvalidRequestException("Phiếu nhập/trả nhà cung cấp cần đối tác");
        }
        if (!supplierType && document.getSupplierId() != null) {
            throw new InvalidRequestException("Loại phiếu này không có nhà cung cấp");
        }
        if (Set.of("PATIENT_RETURN", "SUPPLIER_RETURN", "DISPOSAL", "ADJUSTMENT")
                .contains(document.getType())
                && (document.getNote() == null || document.getNote().isBlank())) {
            throw new InvalidRequestException("Loại phiếu này cần lý do");
        }
        boolean prescriptionType = document.getType().equals("DISPENSE")
                || document.getType().equals("PATIENT_RETURN");
        if ((prescriptionType != (document.getPrescriptionId() != null))
                || (document.getPurchaseRequestId() != null && !document.getType().equals("RECEIPT"))) {
            throw new InvalidRequestException("Loại phiếu này không gắn đơn hoặc đề xuất nhập");
        }
        if (document.getType().equals("DISPENSE")
                && (!document.getStockLocation().equals("COUNTER")
                || !document.getStockBucket().equals("AVAILABLE"))) {
            throw new InvalidRequestException("Chỉ cấp từ tồn khả dụng tại quầy");
        }
        if (document.getType().equals("PATIENT_RETURN")
                && (!document.getStockLocation().equals("COUNTER")
                || !document.getStockBucket().equals("QUARANTINE")
                || document.getNote() == null || document.getNote().isBlank())) {
            throw new InvalidRequestException("Thuốc bệnh nhân trả phải vào quầy cách ly và có lý do");
        }
        for (StockDocumentItem line : lines) {
            boolean adjustment = document.getType().equals("ADJUSTMENT");
            if (!validQuantity(line.getQuantity())
                    || (!adjustment && line.getQuantity().signum() <= 0)
                    || (prescriptionType != (line.getPrescriptionItemId() != null))
                    || (document.getType().equals("PATIENT_RETURN")
                            != (line.getSourceItemId() != null))
                    || (adjustment != (line.getBookQuantity() != null
                            && line.getCountedQuantity() != null))
                    || (line.getUnitCost() != null && (line.getUnitCost().signum() < 0
                    || line.getUnitCost().scale() > 2))) {
                throw new InvalidRequestException("Dòng phiếu kho không hợp lệ");
            }
            if (!document.getType().equals("RECEIPT") && line.getUnitCost() != null) {
                throw new InvalidRequestException("Chỉ phiếu nhập có giá nhập");
            }
            if (adjustment && (line.getBookQuantity().signum() < 0
                    || line.getCountedQuantity().signum() < 0
                    || !validQuantity(line.getBookQuantity())
                    || !validQuantity(line.getCountedQuantity())
                    || !line.getQuantity().equals(line.getCountedQuantity()
                            .subtract(line.getBookQuantity())))) {
                throw new InvalidRequestException("Chênh lệch kiểm kê không khớp tồn sổ và kiểm đếm");
            }
        }
    }

    private void validatePatientReturn(StockDocument document, List<StockDocumentItem> lines) {
        Map<Long, BigDecimal> bySource = new HashMap<>();
        Map<Long, BigDecimal> original = new HashMap<>();
        for (StockDocumentItem line : lines) {
            if (line.getNote() == null || !(line.getNote().startsWith("Tình trạng: SEALED")
                    || line.getNote().startsWith("Tình trạng: OPENED")
                    || line.getNote().startsWith("Tình trạng: DAMAGED")
                    || line.getNote().startsWith("Tình trạng: UNKNOWN"))) {
                throw new InvalidRequestException("Cần tình trạng thuốc trả");
            }
            var source = returnReferences.requirePostedSource(line.getSourceItemId(),
                    document.getPrescriptionId(), line.getPrescriptionItemId(), line.getBatch().getId());
            original.put(source.id(), source.quantity());
            bySource.merge(line.getSourceItemId(), line.getQuantity(), BigDecimal::add);
        }
        for (var entry : bySource.entrySet()) {
            if (returnReferences.postedReturned(entry.getKey()).add(entry.getValue())
                    .compareTo(original.get(entry.getKey())) > 0) {
                throw new BusinessException("PATIENT_RETURN_EXCEEDS_DISPENSE",
                        "Tổng thuốc trả vượt lượng đã cấp từ dòng gốc");
            }
        }
    }

    private void validateDispense(StockDocument document, List<StockDocumentItem> lines,
            IssuedPrescriptionSnapshot prescription) {
        if (document.getOccurredAt().isBefore(prescription.issuedAt())) {
            throw new InvalidRequestException("Thời điểm cấp trước khi phát hành đơn");
        }
        Map<Long, PrescriptionLineResponse> prescribed = prescription.items().stream()
                .collect(Collectors.toMap(PrescriptionLineResponse::id, Function.identity()));
        Map<Long, BigDecimal> byLine = new HashMap<>();
        var today = batchRules.businessDate();
        for (StockDocumentItem line : lines) {
            PrescriptionLineResponse expected = prescribed.get(line.getPrescriptionItemId());
            if (expected == null || !expected.medicineId().equals(line.getBatch().getMedicine().getId())) {
                throw new BusinessException("DISPENSE_LINE_MISMATCH",
                        "Dòng đơn hoặc lô không khớp thuốc trong đơn");
            }
            if (!batchRules.isDispensable(line.getBatch(), today)) {
                throw new BusinessException("BATCH_NOT_DISPENSABLE",
                        "Lô bị khóa, hết hạn hoặc thuốc ngừng hoạt động");
            }
            byLine.merge(line.getPrescriptionItemId(), line.getQuantity(), BigDecimal::add);
        }
        for (var entry : byLine.entrySet()) {
            BigDecimal allowed = prescribed.get(entry.getKey()).quantity()
                    .subtract(dispensed.postedQuantity(entry.getKey()));
            if (entry.getValue().compareTo(allowed) > 0) {
                throw new BusinessException("DISPENSE_EXCEEDS_PRESCRIPTION",
                        "Lượng cấp vượt lượng còn lại của dòng đơn");
            }
        }
    }

    private boolean validQuantity(BigDecimal quantity) {
        return quantity != null && quantity.scale() <= 3
                && quantity.precision() - quantity.scale() <= 11;
    }

    private boolean location(String value) {
        return "MAIN".equals(value) || "COUNTER".equals(value);
    }

    private boolean bucket(String value) {
        return "AVAILABLE".equals(value) || "QUARANTINE".equals(value);
    }

    private void add(Map<StockPosition, BigDecimal> deltas, StockPosition position, BigDecimal amount) {
        deltas.merge(position, amount, BigDecimal::add);
    }

    private void requirePharmacist() {
        if (!accounts.hasActiveRole(actor.userId(), AccountRole.PHARMACIST)) {
            throw new BusinessException("PHARMACIST_INACTIVE", "Tài khoản dược sĩ không hoạt động");
        }
    }
}
