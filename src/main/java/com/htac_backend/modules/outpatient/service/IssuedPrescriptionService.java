package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.outpatient.dto.IssuedPrescriptionSnapshot;
import com.htac_backend.modules.outpatient.dto.PrescriptionLineResponse;
import com.htac_backend.modules.outpatient.entity.Prescription;
import com.htac_backend.modules.outpatient.entity.PrescriptionItem;
import com.htac_backend.modules.outpatient.entity.PrescriptionStatus;
import com.htac_backend.modules.outpatient.repository.PrescriptionItemRepository;
import com.htac_backend.modules.outpatient.repository.PrescriptionRepository;
import com.htac_backend.modules.pharmacy.service.MedicineDisplayLookupService;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M05 contract for M06: caller keeps this prescription lock until dispensing commits. */
@Service
public class IssuedPrescriptionService {
    private final PrescriptionRepository prescriptions;
    private final PrescriptionItemRepository items;
    private final MedicineDisplayLookupService medicines;

    public IssuedPrescriptionService(PrescriptionRepository prescriptions,
            PrescriptionItemRepository items, MedicineDisplayLookupService medicines) {
        this.prescriptions = prescriptions;
        this.items = items;
        this.medicines = medicines;
    }

    @PreAuthorize("hasRole('PHARMACIST')")
    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedPrescriptionSnapshot lockForDispensing(Long prescriptionId) {
        Prescription prescription = prescriptions.findByIdForUpdate(prescriptionId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn thuốc"));
        if (prescription.getStatus() != PrescriptionStatus.ISSUED) {
            throw new BusinessException("PRESCRIPTION_NOT_ISSUED", "Chỉ đơn đã phát hành được cấp thuốc");
        }
        var lines = items.findByPrescriptionIdOrderById(prescriptionId);
        var labels = medicines.labels(lines.stream().map(PrescriptionItem::getMedicineId).toList());
        return new IssuedPrescriptionSnapshot(prescriptionId, prescription.getVisitId(),
                prescription.getDoctorId(), prescription.getIssuedAt(), lines.stream()
                .map(line -> PrescriptionLineResponse.from(line, labels.get(line.getMedicineId()))).toList());
    }
}
