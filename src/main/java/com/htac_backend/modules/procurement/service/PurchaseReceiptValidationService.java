package com.htac_backend.modules.procurement.service;

import com.htac_backend.modules.pharmacy.service.PostedReceiptQuantityService;
import com.htac_backend.modules.procurement.repository.PurchaseRequestItemRepository;
import com.htac_backend.modules.procurement.repository.PurchaseRequestRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Called by M06 within its draft/post transaction; the request row serializes receipt posting. */
@Service
public class PurchaseReceiptValidationService {
    private final PurchaseRequestRepository requests;
    private final PurchaseRequestItemRepository items;
    private final PostedReceiptQuantityService receipts;

    public PurchaseReceiptValidationService(PurchaseRequestRepository requests,
            PurchaseRequestItemRepository items, PostedReceiptQuantityService receipts) {
        this.requests = requests;
        this.items = items;
        this.receipts = receipts;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void check(Long requestId, Long supplierId, Map<Long, BigDecimal> incoming) {
        if (requestId == null) return;
        var request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đề xuất nhập"));
        if (!"APPROVED".equals(request.getStatus()) || request.getClosedAt() != null) {
            throw new BusinessException("PURCHASE_NOT_RECEIVABLE", "Đề xuất chưa duyệt hoặc đã kết thúc");
        }
        if (!request.getSupplierId().equals(supplierId)) {
            throw new BusinessException("PURCHASE_SUPPLIER_MISMATCH", "Nhà cung cấp không khớp đề xuất");
        }
        Map<Long, BigDecimal> approved = new HashMap<>();
        items.findByRequestIdOrderById(requestId).forEach(item ->
                approved.put(item.getMedicineId(), item.getQuantity()));
        Map<Long, BigDecimal> received = receipts.receivedByMedicine(requestId);
        for (var entry : incoming.entrySet()) {
            BigDecimal allowed = approved.get(entry.getKey());
            if (allowed == null) {
                throw new BusinessException("PURCHASE_MEDICINE_MISMATCH", "Thuốc không có trong đề xuất");
            }
            if (entry.getValue() == null || entry.getValue().signum() <= 0
                    || received.getOrDefault(entry.getKey(), BigDecimal.ZERO)
                    .add(entry.getValue()).compareTo(allowed) > 0) {
                throw new BusinessException("PURCHASE_QUANTITY_EXCEEDED", "Lượng nhận vượt lượng duyệt");
            }
        }
    }
}
