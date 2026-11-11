package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.pharmacy.dto.InboundStockLineRequest;
import com.htac_backend.modules.pharmacy.dto.OpeningStockRequest;
import com.htac_backend.modules.pharmacy.dto.ReceiptStockRequest;
import com.htac_backend.modules.pharmacy.dto.StockDocumentResponse;
import com.htac_backend.modules.pharmacy.repository.StockDocumentItemRepository;
import com.htac_backend.modules.pharmacy.repository.SupplierRepository;
import com.htac_backend.modules.procurement.service.PurchaseReceiptValidationService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.HashMap;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboundStockService {
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final StockDraftFactory factory;
    private final StockDocumentItemRepository items;
    private final SupplierRepository suppliers;
    private final CurrentPharmacistActor actor;
    private final AccountRoleLookupService accounts;
    private final ProcurementCatalogReferenceService catalog;
    private final PurchaseReceiptValidationService procurement;

    public InboundStockService(StockDraftFactory factory, StockDocumentItemRepository items,
            SupplierRepository suppliers, CurrentPharmacistActor actor,
            AccountRoleLookupService accounts, ProcurementCatalogReferenceService catalog,
            PurchaseReceiptValidationService procurement) {
        this.factory = factory;
        this.items = items;
        this.suppliers = suppliers;
        this.actor = actor;
        this.accounts = accounts;
        this.catalog = catalog;
        this.procurement = procurement;
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockDocumentResponse opening(OpeningStockRequest body) {
        requirePharmacist();
        if (body == null || body.businessDate() == null
                || body.businessDate().isAfter(LocalDate.now(HOSPITAL_ZONE))) {
            throw new InvalidRequestException("Ngày tồn đầu kỳ không hợp lệ");
        }
        List<StockDraftFactory.DraftLine> lines = lines(body.items(), false);
        var document = factory.create("OPENING",
                body.businessDate().atStartOfDay(HOSPITAL_ZONE).toInstant(),
                body.stockLocation(), body.stockBucket(), null, null, null, null, null,
                body.note(), lines);
        return StockDocumentResponse.from(document, items.findDetailsByDocumentId(document.getId()));
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockDocumentResponse receipt(ReceiptStockRequest body) {
        requirePharmacist();
        if (body == null || body.occurredAt() == null || body.supplierId() == null) {
            throw new InvalidRequestException("Phiếu nhập cần thời điểm và nhà cung cấp");
        }
        var supplier = suppliers.findByIdForUpdate(body.supplierId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhà cung cấp"));
        if (!supplier.isActive()) {
            throw new BusinessException("SUPPLIER_INACTIVE", "Nhà cung cấp đã ngừng hoạt động");
        }
        List<StockDraftFactory.DraftLine> lines = lines(body.items(), true);
        if (body.purchaseRequestId() != null) {
            Map<Long, Long> medicines = catalog.medicinesForBatches(
                    lines.stream().map(StockDraftFactory.DraftLine::batchId).toList());
            Map<Long, BigDecimal> incoming = new HashMap<>();
            lines.forEach(line -> incoming.merge(medicines.get(line.batchId()),
                    line.quantity(), BigDecimal::add));
            procurement.check(body.purchaseRequestId(), body.supplierId(), incoming);
        }
        var document = factory.create("RECEIPT", body.occurredAt().toInstant(),
                body.stockLocation(), body.stockBucket(), null, null, body.supplierId(),
                null, body.purchaseRequestId(), body.note(), lines);
        return StockDocumentResponse.from(document, items.findDetailsByDocumentId(document.getId()));
    }

    private List<StockDraftFactory.DraftLine> lines(List<InboundStockLineRequest> source,
            boolean receipt) {
        if (source == null || source.isEmpty()) {
            throw new InvalidRequestException("Phiếu cần ít nhất một dòng");
        }
        return source.stream().map(line -> {
            if (line == null || line.quantity() == null || line.quantity().signum() <= 0
                    || (!receipt && line.unitCost() != null)) {
                throw new InvalidRequestException("Lượng hoặc giá nhập không hợp lệ");
            }
            return new StockDraftFactory.DraftLine(line.batchId(), line.quantity(),
                    line.unitCost(), null, null, null, null, line.note());
        }).toList();
    }

    private void requirePharmacist() {
        if (!accounts.hasActiveRole(actor.userId(), AccountRole.PHARMACIST)) {
            throw new BusinessException("PHARMACIST_INACTIVE", "Tài khoản dược sĩ không hoạt động");
        }
    }
}
