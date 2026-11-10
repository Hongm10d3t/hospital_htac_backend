package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.pharmacy.dto.StockDocumentResponse;
import com.htac_backend.modules.pharmacy.dto.TransferStockRequest;
import com.htac_backend.modules.pharmacy.repository.StockDocumentItemRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferStockService {
    public static final String RELEASE_PREFIX = "Đã xác nhận xử lý cách ly: ";
    private final StockDraftFactory factory;
    private final StockDocumentItemRepository items;
    private final CurrentPharmacistActor actor;
    private final AccountRoleLookupService accounts;

    public TransferStockService(StockDraftFactory factory, StockDocumentItemRepository items,
            CurrentPharmacistActor actor, AccountRoleLookupService accounts) {
        this.factory = factory;
        this.items = items;
        this.actor = actor;
        this.accounts = accounts;
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockDocumentResponse create(TransferStockRequest body) {
        requirePharmacist();
        if (body == null || body.occurredAt() == null
                || !location(body.stockLocation()) || !location(body.destinationLocation())
                || !bucket(body.stockBucket()) || !bucket(body.destinationBucket())
                || (body.stockLocation().equals(body.destinationLocation())
                && body.stockBucket().equals(body.destinationBucket()))) {
            throw new InvalidRequestException("Nguồn và đích chuyển kho không hợp lệ");
        }
        boolean release = "QUARANTINE".equals(body.stockBucket())
                && "AVAILABLE".equals(body.destinationBucket());
        boolean quarantine = "AVAILABLE".equals(body.stockBucket())
                && "QUARANTINE".equals(body.destinationBucket());
        if ((release && !body.releaseConfirmed()) || ((release || quarantine)
                && (body.note() == null || body.note().isBlank()))
                || (!release && body.releaseConfirmed())) {
            throw new InvalidRequestException("Chuyển nhóm tồn cần xác nhận và lý do phù hợp");
        }
        if (body.items() == null || body.items().isEmpty()) {
            throw new InvalidRequestException("Phiếu cần ít nhất một dòng");
        }
        var lines = body.items().stream().map(line -> {
            if (line == null || line.quantity() == null || line.quantity().signum() <= 0
                    || line.unitCost() != null) {
                throw new InvalidRequestException("Dòng chuyển kho không hợp lệ");
            }
            return new StockDraftFactory.DraftLine(line.batchId(), line.quantity(),
                    null, null, null, null, null, line.note());
        }).toList();
        String note = release ? RELEASE_PREFIX + body.note().trim()
                : body.note() == null ? null : body.note().trim();
        var document = factory.create("TRANSFER", body.occurredAt().toInstant(),
                body.stockLocation(), body.stockBucket(), body.destinationLocation(),
                body.destinationBucket(), null, null, null, note, lines);
        return StockDocumentResponse.from(document, items.findDetailsByDocumentId(document.getId()));
    }

    private boolean location(String value) {
        return "MAIN".equals(value) || "COUNTER".equals(value);
    }

    private boolean bucket(String value) {
        return "AVAILABLE".equals(value) || "QUARANTINE".equals(value);
    }

    private void requirePharmacist() {
        if (!accounts.hasActiveRole(actor.userId(), AccountRole.PHARMACIST)) {
            throw new BusinessException("PHARMACIST_INACTIVE", "Tài khoản dược sĩ không hoạt động");
        }
    }
}
