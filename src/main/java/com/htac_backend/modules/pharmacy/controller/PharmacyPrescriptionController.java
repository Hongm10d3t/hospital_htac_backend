package com.htac_backend.modules.pharmacy.controller;

import com.htac_backend.modules.pharmacy.dto.DispensingSnapshotResponse;
import com.htac_backend.modules.pharmacy.service.DispenseStockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacy/prescriptions")
public class PharmacyPrescriptionController {
    private final DispenseStockService dispensing;

    public PharmacyPrescriptionController(DispenseStockService dispensing) {
        this.dispensing = dispensing;
    }

    @GetMapping("/{id}/dispensing")
    public DispensingSnapshotResponse snapshot(@PathVariable Long id) {
        return dispensing.snapshot(id);
    }
}
