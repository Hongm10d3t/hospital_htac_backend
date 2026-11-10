package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.pharmacy.entity.StockDocument;
import com.htac_backend.modules.pharmacy.entity.StockDocumentItem;
import com.htac_backend.modules.pharmacy.repository.MedicineBatchRepository;
import com.htac_backend.modules.pharmacy.repository.StockDocumentItemRepository;
import com.htac_backend.modules.pharmacy.repository.StockDocumentRepository;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M06-owned draft persistence shared by the separate stock workflows. */
@Service
public class StockDraftFactory {
    private final StockDocumentRepository documents;
    private final StockDocumentItemRepository items;
    private final MedicineBatchRepository batches;

    public StockDraftFactory(StockDocumentRepository documents,
            StockDocumentItemRepository items, MedicineBatchRepository batches) {
        this.documents = documents;
        this.items = items;
        this.batches = batches;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockDocument create(String type, Instant occurredAt, String location, String bucket,
            String destinationLocation, String destinationBucket, Long supplierId,
            Long prescriptionId, Long purchaseRequestId, String note,
            List<DraftLine> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > 100
                || occurredAt == null || !validLocation(location) || !validBucket(bucket)
                || (note != null && note.length() > 2000)) {
            throw new InvalidRequestException("Phiếu kho không hợp lệ");
        }
        var seen = new HashSet<String>();
        for (DraftLine line : lines) {
            if (line.batchId() == null || line.batchId() <= 0
                    || line.quantity() == null || line.quantity().scale() > 3
                    || line.quantity().precision() - line.quantity().scale() > 11
                    || (line.unitCost() != null && (line.unitCost().signum() < 0
                    || line.unitCost().scale() > 2
                    || line.unitCost().precision() - line.unitCost().scale() > 12))
                    || !seen.add(line.batchId() + ":" + line.prescriptionItemId() + ":" + line.sourceItemId())) {
                throw new InvalidRequestException("Dòng phiếu kho không hợp lệ hoặc bị trùng");
            }
        }
        StockDocument document = documents.saveAndFlush(new StockDocument(code(), type,
                occurredAt, location, bucket, destinationLocation, destinationBucket,
                supplierId, prescriptionId, purchaseRequestId, note));
        for (DraftLine line : lines) {
            var batch = batches.findById(line.batchId())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy lô thuốc"));
            items.save(new StockDocumentItem(document, batch, line.quantity(), line.unitCost(),
                    line.prescriptionItemId(), line.sourceItemId(), line.bookQuantity(),
                    line.countedQuantity(), line.note()));
        }
        items.flush();
        return document;
    }

    public record DraftLine(Long batchId, BigDecimal quantity, BigDecimal unitCost,
            Long prescriptionItemId, Long sourceItemId, BigDecimal bookQuantity,
            BigDecimal countedQuantity, String note) { }

    private String code() {
        return "STK" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
    }

    private boolean validLocation(String value) {
        return "MAIN".equals(value) || "COUNTER".equals(value);
    }

    private boolean validBucket(String value) {
        return "AVAILABLE".equals(value) || "QUARANTINE".equals(value);
    }
}
