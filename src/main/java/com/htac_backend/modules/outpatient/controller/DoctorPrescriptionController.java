package com.htac_backend.modules.outpatient.controller;

import com.htac_backend.modules.outpatient.dto.CreatePrescriptionRequest;
import com.htac_backend.modules.outpatient.dto.PrescriptionResponse;
import com.htac_backend.modules.outpatient.service.DoctorPrescriptionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doctor")
public class DoctorPrescriptionController {
    private final DoctorPrescriptionService prescriptions;

    public DoctorPrescriptionController(DoctorPrescriptionService prescriptions) {
        this.prescriptions = prescriptions;
    }

    @PostMapping("/visits/{visitId}/prescriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public PrescriptionResponse create(@PathVariable Long visitId,
            @Valid @RequestBody CreatePrescriptionRequest request) {
        return prescriptions.create(visitId, request);
    }

    @GetMapping("/visits/{visitId}/prescriptions")
    public List<PrescriptionResponse> forVisit(@PathVariable Long visitId) {
        return prescriptions.forVisit(visitId);
    }

    @GetMapping("/prescriptions/{prescriptionId}")
    public PrescriptionResponse get(@PathVariable Long prescriptionId) {
        return prescriptions.get(prescriptionId);
    }

    @PostMapping("/prescriptions/{prescriptionId}/issue")
    public PrescriptionResponse issue(@PathVariable Long prescriptionId) {
        return prescriptions.issue(prescriptionId);
    }
}
