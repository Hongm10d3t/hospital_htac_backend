package com.htac_backend.modules.pharmacy.service;

import com.htac_backend.modules.pharmacy.repository.MedicineBatchRepository;
import com.htac_backend.modules.pharmacy.repository.MedicineRepository;
import com.htac_backend.modules.pharmacy.repository.SupplierRepository;
import com.htac_backend.shared.api.BusinessException;
import com.htac_backend.shared.api.NotFoundException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M06-owned catalog lookup for M07. Call within the coordinating transaction. */
@Service
public class ProcurementCatalogReferenceService {
    private final MedicineRepository medicines;
    private final MedicineBatchRepository batches;
    private final SupplierRepository suppliers;

    public ProcurementCatalogReferenceService(MedicineRepository medicines,
            MedicineBatchRepository batches, SupplierRepository suppliers) {
        this.medicines = medicines;
        this.batches = batches;
        this.suppliers = suppliers;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public void requireActiveSupplier(Long id) {
        var supplier = suppliers.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy nhà cung cấp"));
        if (!supplier.isActive()) {
            throw new BusinessException("SUPPLIER_INACTIVE", "Nhà cung cấp đã ngừng hoạt động");
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public void requireActiveMedicine(Long id) {
        var medicine = medicines.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thuốc"));
        if (!medicine.isActive()) {
            throw new BusinessException("MEDICINE_INACTIVE", "Thuốc đã ngừng hoạt động");
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Map<Long, Long> medicinesForBatches(Iterable<Long> batchIds) {
        Map<Long, Long> result = new HashMap<>();
        for (Long id : batchIds) {
            var batch = batches.findDetail(id)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy lô thuốc"));
            result.put(id, batch.getMedicine().getId());
        }
        return result;
    }
}
