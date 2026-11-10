package com.htac_backend.modules.outpatient.repository;

import com.htac_backend.modules.outpatient.entity.PrescriptionItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long> {
    List<PrescriptionItem> findByPrescriptionIdOrderById(Long prescriptionId);
    void deleteByPrescriptionId(Long prescriptionId);
}
