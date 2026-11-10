package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.service.AccountRoleLookupService;
import com.htac_backend.modules.outpatient.service.IssuedPrescriptionService;
import com.htac_backend.modules.pharmacy.dto.PatientReturnRequest;
import com.htac_backend.modules.pharmacy.dto.StockDocumentResponse;
import com.htac_backend.modules.pharmacy.repository.StockDocumentItemRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.InvalidRequestException;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientReturnService {
    private static final Set<String> CONDITIONS = Set.of("SEALED", "OPENED", "DAMAGED", "UNKNOWN");
    private final IssuedPrescriptionService prescriptions;
    private final PatientReturnReferenceService references;
    private final StockDraftFactory factory;
    private final StockDocumentItemRepository items;
    private final CurrentPharmacistActor actor;
    private final AccountRoleLookupService accounts;

    public PatientReturnService(IssuedPrescriptionService prescriptions,
            PatientReturnReferenceService references, StockDraftFactory factory,
            StockDocumentItemRepository items, CurrentPharmacistActor actor,
            AccountRoleLookupService accounts) {
        this.prescriptions = prescriptions;
        this.references = references;
        this.factory = factory;
        this.items = items;
        this.actor = actor;
        this.accounts = accounts;
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockDocumentResponse create(PatientReturnRequest body) {
        requirePharmacist();
        if (body == null || body.prescriptionId() == null || body.occurredAt() == null
                || body.reason() == null || body.reason().isBlank()
                || body.items() == null || body.items().isEmpty()) {
            throw new InvalidRequestException("Phiếu bệnh nhân trả không hợp lệ");
        }
        prescriptions.lockForDispensing(body.prescriptionId());
        List<StockDraftFactory.DraftLine> lines = body.items().stream().map(line -> {
            if (line == null || line.sourceItemId() == null || line.prescriptionItemId() == null
                    || line.batchId() == null || line.quantity() == null
                    || line.quantity().signum() <= 0 || line.condition() == null
                    || !CONDITIONS.contains(line.condition())) {
                throw new InvalidRequestException("Dòng thuốc trả không hợp lệ");
            }
            var source = references.requirePostedSource(line.sourceItemId(), body.prescriptionId(),
                    line.prescriptionItemId(), line.batchId());
            if (references.postedReturned(line.sourceItemId()).add(line.quantity())
                    .compareTo(source.quantity()) > 0) {
                throw new BusinessException("PATIENT_RETURN_EXCEEDS_DISPENSE",
                        "Tổng thuốc trả vượt lượng đã cấp từ dòng gốc");
            }
            String note = "Tình trạng: " + line.condition()
                    + (line.note() == null || line.note().isBlank() ? "" : "; " + line.note().trim());
            return new StockDraftFactory.DraftLine(line.batchId(), line.quantity(), null,
                    line.prescriptionItemId(), line.sourceItemId(), null, null, note);
        }).toList();
        var document = factory.create("PATIENT_RETURN", body.occurredAt().toInstant(),
                "COUNTER", "QUARANTINE", null, null, null, body.prescriptionId(),
                null, body.reason().trim(), lines);
        return StockDocumentResponse.from(document, items.findDetailsByDocumentId(document.getId()));
    }

    private void requirePharmacist() {
        if (!accounts.hasActiveRole(actor.userId(), AccountRole.PHARMACIST)) {
            throw new BusinessException("PHARMACIST_INACTIVE", "Tài khoản dược sĩ không hoạt động");
        }
    }
}
