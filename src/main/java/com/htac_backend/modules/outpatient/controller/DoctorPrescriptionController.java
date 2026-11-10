package com.htac_backend.modules.outpatient.controller;

import com.htac_backend.modules.outpatient.dto.CreatePrescriptionRequest;
import com.htac_backend.modules.outpatient.dto.PrescriptionResponse;
import com.htac_backend.modules.outpatient.dto.UpdatePrescriptionRequest;
import com.htac_backend.modules.outpatient.dto.CancelPrescriptionRequest;
import com.htac_backend.modules.outpatient.service.DoctorPrescriptionAmendmentService;
import com.htac_backend.modules.outpatient.service.DoctorPrescriptionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doctor")
public class DoctorPrescriptionController {
    private final DoctorPrescriptionService prescriptions;
    private final DoctorPrescriptionAmendmentService amendments;

    public DoctorPrescriptionController(DoctorPrescriptionService prescriptions,
            DoctorPrescriptionAmendmentService amendments) {
        this.prescriptions = prescriptions;
        this.amendments = amendments;
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

    @PutMapping("/prescriptions/{prescriptionId}")
    public PrescriptionResponse update(@PathVariable Long prescriptionId,
            @Valid @RequestBody UpdatePrescriptionRequest request) {
        return amendments.update(prescriptionId, request);
    }

    @PostMapping("/prescriptions/{prescriptionId}/cancel")
    public PrescriptionResponse cancel(@PathVariable Long prescriptionId,
            @Valid @RequestBody CancelPrescriptionRequest request) {
        return amendments.cancel(prescriptionId, request.reason());
    }
}
