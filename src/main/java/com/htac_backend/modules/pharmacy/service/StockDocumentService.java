package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.pharmacy.dto.StockDocumentResponse;
import com.htac_backend.modules.pharmacy.entity.StockDocument;
import com.htac_backend.modules.pharmacy.entity.StockDocumentItem;
import com.htac_backend.modules.pharmacy.repository.MedicineBatchRepository;
import com.htac_backend.modules.pharmacy.repository.StockDocumentItemRepository;
import com.htac_backend.modules.pharmacy.repository.StockDocumentRepository;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockDocumentService {
    private static final Set<String> ENABLED_TYPES = Set.of("OPENING", "RECEIPT", "TRANSFER");
    private final StockDocumentRepository documents;
    private final StockDocumentItemRepository items;
    private final MedicineBatchRepository batches;
    private final StockBalanceService balances;
    private final CurrentPharmacistActor actor;
    private final AccountRoleLookupService accounts;

    public StockDocumentService(StockDocumentRepository documents,
            StockDocumentItemRepository items, MedicineBatchRepository batches,
            StockBalanceService balances, CurrentPharmacistActor actor,
            AccountRoleLookupService accounts) {
        this.documents = documents;
        this.items = items;
        this.batches = batches;
        this.balances = balances;
        this.actor = actor;
        this.accounts = accounts;
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
        List<StockDocumentItem> lines = items.findDetailsByDocumentId(id);
        if (document.getStatus().equals("POSTED")) {
            return StockDocumentResponse.from(document, lines);
        }
        if (!document.getStatus().equals("DRAFT")) {
            throw new BusinessException("STOCK_DOCUMENT_FINAL", "Phiếu đã hủy không thể ghi sổ");
        }
        validate(document, lines);
        // One batch row coordinates all writers, including the first document for an empty balance.
        lines.stream().map(line -> line.getBatch().getId()).distinct().sorted()
                .forEach(batchId -> batches.findByIdForUpdate(batchId)
                        .orElseThrow(() -> new NotFoundException("Không tìm thấy lô thuốc")));
        Map<StockPosition, BigDecimal> deltas = new HashMap<>();
        for (StockDocumentItem line : lines) {
            StockPosition source = new StockPosition(line.getBatch().getId(),
                    document.getStockLocation(), document.getStockBucket());
            BigDecimal quantity = line.getQuantity();
            if (document.getType().equals("TRANSFER")) {
                add(deltas, source, quantity.negate());
                add(deltas, new StockPosition(line.getBatch().getId(),
                        document.getDestinationLocation(), document.getDestinationBucket()), quantity);
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
        document.post(actor.userId(), Instant.now().truncatedTo(ChronoUnit.MICROS));
        documents.flush();
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
        if (document.getType().equals("RECEIPT") && document.getSupplierId() == null) {
            throw new InvalidRequestException("Phiếu nhập cần nhà cung cấp");
        }
        if (!document.getType().equals("RECEIPT") && document.getSupplierId() != null) {
            throw new InvalidRequestException("Loại phiếu này không có nhà cung cấp");
        }
        if (document.getPrescriptionId() != null || document.getPurchaseRequestId() != null) {
            throw new InvalidRequestException("Loại phiếu này không gắn đơn hoặc đề xuất nhập");
        }
        for (StockDocumentItem line : lines) {
            if (!validQuantity(line.getQuantity()) || line.getQuantity().signum() <= 0
                    || line.getPrescriptionItemId() != null || line.getSourceItemId() != null
                    || line.getBookQuantity() != null || line.getCountedQuantity() != null
                    || (line.getUnitCost() != null && (line.getUnitCost().signum() < 0
                    || line.getUnitCost().scale() > 2))) {
                throw new InvalidRequestException("Dòng phiếu kho không hợp lệ");
            }
            if (!document.getType().equals("RECEIPT") && line.getUnitCost() != null) {
                throw new InvalidRequestException("Chỉ phiếu nhập có giá nhập");
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
