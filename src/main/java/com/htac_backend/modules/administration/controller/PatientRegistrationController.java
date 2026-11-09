package com.htac_backend.modules.administration.controller;

import com.htac_backend.modules.administration.dto.PatientRegistrationResponse;
import com.htac_backend.modules.administration.dto.RegisterPatientRequest;
import com.htac_backend.modules.administration.service.PatientAccountRegistrationService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PatientRegistrationController {
    private final PatientAccountRegistrationService registrations;

    public PatientRegistrationController(PatientAccountRegistrationService registrations) {
        this.registrations = registrations;
    }

    @PostMapping("/api/v1/public/auth/register")
    public ResponseEntity<PatientRegistrationResponse> register(@Valid @RequestBody RegisterPatientRequest request) {
        PatientRegistrationResponse created = registrations.register(request);
        return ResponseEntity.created(URI.create("/api/v1/auth/me"))
                .cacheControl(CacheControl.noStore()).body(created);
    }
}
