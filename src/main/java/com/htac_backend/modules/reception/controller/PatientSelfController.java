package com.htac_backend.modules.reception.controller;

import com.htac_backend.modules.reception.dto.PatientResponse;
import com.htac_backend.modules.reception.service.PatientDirectoryService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients/me")
public class PatientSelfController {
    private final PatientDirectoryService patients;

    public PatientSelfController(PatientDirectoryService patients) {
        this.patients = patients;
    }

    @GetMapping
    public ResponseEntity<PatientResponse> own() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(patients.own());
    }
}
