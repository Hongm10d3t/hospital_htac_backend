package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.pharmacy.dto.AdjustmentRequest;
import com.htac_backend.modules.pharmacy.dto.StockDocumentResponse;
import com.htac_backend.modules.pharmacy.dto.StockExitRequest;
import com.htac_backend.modules.pharmacy.repository.MedicineBatchRepository;
import com.htac_backend.modules.pharmacy.repository.StockDocumentItemRepository;
import com.htac_backend.modules.pharmacy.repository.SupplierRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockCorrectionService {
    private final StockDraftFactory factory;
    private final StockDocumentItemRepository items;
    private final MedicineBatchRepository batches;
    private final StockBalanceService balances;
    private final SupplierRepository suppliers;
    private final CurrentPharmacistActor actor;
    private final AccountRoleLookupService accounts;

    public StockCorrectionService(StockDraftFactory factory, StockDocumentItemRepository items,
            MedicineBatchRepository batches, StockBalanceService balances,
            SupplierRepository suppliers, CurrentPharmacistActor actor,
            AccountRoleLookupService accounts) {
        this.factory = factory;
        this.items = items;
        this.batches = batches;
        this.balances = balances;
        this.suppliers = suppliers;
        this.actor = actor;
        this.accounts = accounts;
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockDocumentResponse supplierReturn(StockExitRequest body) {
        requirePharmacist();
        validateExit(body, true);
        suppliers.findById(body.supplierId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhà cung cấp"));
        return createExit("SUPPLIER_RETURN", body);
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockDocumentResponse disposal(StockExitRequest body) {
        requirePharmacist();
        validateExit(body, false);
        return createExit("DISPOSAL", body);
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockDocumentResponse adjustment(AdjustmentRequest body) {
        requirePharmacist();
        if (body == null || body.occurredAt() == null || body.reason() == null
                || body.reason().isBlank() || body.items() == null || body.items().isEmpty()) {
            throw new InvalidRequestException("Phiếu kiểm kê không hợp lệ");
        }
        if (body.items().stream().anyMatch(line -> line == null || line.batchId() == null
                || line.bookQuantity() == null || line.countedQuantity() == null
                || line.bookQuantity().signum() < 0 || line.countedQuantity().signum() < 0)) {
            throw new InvalidRequestException("Dòng kiểm kê không hợp lệ");
        }
        body.items().stream().map(line -> line.batchId()).distinct().sorted()
                .forEach(id -> batches.findByIdForUpdate(id)
                        .orElseThrow(() -> new NotFoundException("Không tìm thấy lô thuốc")));
        List<StockDraftFactory.DraftLine> lines = body.items().stream().map(line -> {
            BigDecimal actual = balances.balance(new StockPosition(line.batchId(),
                    body.stockLocation(), body.stockBucket()));
            if (actual.compareTo(line.bookQuantity()) != 0) {
                throw new BusinessException("STOCK_COUNT_STALE",
                        "Tồn sổ đã thay đổi; đọc lại tồn và kiểm đếm lại");
            }
            return new StockDraftFactory.DraftLine(line.batchId(),
                    line.countedQuantity().subtract(line.bookQuantity()), null,
                    null, null, line.bookQuantity(), line.countedQuantity(), line.note());
        }).toList();
        var document = factory.create("ADJUSTMENT", body.occurredAt().toInstant(),
                body.stockLocation(), body.stockBucket(), null, null, null, null, null,
                body.reason().trim(), lines);
        return StockDocumentResponse.from(document, items.findDetailsByDocumentId(document.getId()));
    }

    private StockDocumentResponse createExit(String type, StockExitRequest body) {
        List<StockDraftFactory.DraftLine> lines = body.items().stream().map(line -> {
            if (line == null || line.batchId() == null || line.quantity() == null
                    || line.quantity().signum() <= 0) {
                throw new InvalidRequestException("Dòng xuất kho không hợp lệ");
            }
            return new StockDraftFactory.DraftLine(line.batchId(), line.quantity(), null,
                    null, null, null, null, line.note());
        }).toList();
        var document = factory.create(type, body.occurredAt().toInstant(),
                body.stockLocation(), body.stockBucket(), null, null,
                body.supplierId(), null, null, body.reason().trim(), lines);
        return StockDocumentResponse.from(document, items.findDetailsByDocumentId(document.getId()));
    }

    private void validateExit(StockExitRequest body, boolean supplierReturn) {
        if (body == null || body.occurredAt() == null || body.reason() == null
                || body.reason().isBlank() || body.items() == null || body.items().isEmpty()
                || (supplierReturn && body.supplierId() == null)
                || (!supplierReturn && body.supplierId() != null)) {
            throw new InvalidRequestException("Phiếu xuất kho thiếu đối tác, lý do hoặc dòng");
        }
    }

    private void requirePharmacist() {
        if (!accounts.hasActiveRole(actor.userId(), AccountRole.PHARMACIST)) {
            throw new BusinessException("PHARMACIST_INACTIVE", "Tài khoản dược sĩ không hoạt động");
        }
    }
}
