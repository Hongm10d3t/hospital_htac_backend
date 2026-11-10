package com.htac_backend.modules.pharmacy.controller;

import com.htac_backend.modules.pharmacy.dto.DispensingSnapshotResponse;
import com.htac_backend.modules.pharmacy.service.DispenseStockService;
import com.htac_backend.modules.pharmacy.service.UnfilledReviewService;
import com.htac_backend.modules.pharmacy.dto.ReviewUnfilledRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pharmacy/prescriptions")
public class PharmacyPrescriptionController {
    private final DispenseStockService dispensing;
    private final UnfilledReviewService reviews;

    public PharmacyPrescriptionController(DispenseStockService dispensing, UnfilledReviewService reviews) {
        this.dispensing = dispensing;
        this.reviews = reviews;
    }

    @GetMapping("/{id}/dispensing")
    public DispensingSnapshotResponse snapshot(@PathVariable Long id) {
        return dispensing.snapshot(id);
    }

    @PostMapping("/{id}/dispensing-review")
    public DispensingSnapshotResponse review(@PathVariable Long id,
            @Valid @RequestBody ReviewUnfilledRequest body) {
        return reviews.review(id, body);
    }
}
