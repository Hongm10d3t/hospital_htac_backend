package com.htac_backend.modules.reception.controller;

import com.htac_backend.modules.reception.dto.CreatePatientRequest;
import com.htac_backend.modules.reception.dto.PatientResponse;
import com.htac_backend.modules.reception.dto.UpdatePatientRequest;
import com.htac_backend.modules.reception.service.PatientDirectoryService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reception/patients")
public class PatientDirectoryController {
    private final PatientDirectoryService patients;

    public PatientDirectoryController(PatientDirectoryService patients) {
        this.patients = patients;
    }

    @GetMapping
    public ResponseEntity<PageResponse<PatientResponse>> search(
            @RequestParam(required = false) String patientCode,
            @RequestParam(required = false) String citizenId,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) LocalDate dateOfBirth,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(patients.search(patientCode,
                citizenId, fullName, dateOfBirth, PageQuery.parse(page, size, sort, "id", Set.of("id"))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PatientResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(patients.get(id));
    }

    @PostMapping
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody CreatePatientRequest body) {
        PatientResponse created = patients.create(body);
        return ResponseEntity.created(URI.create("/api/v1/reception/patients/" + created.id()))
                .cacheControl(CacheControl.noStore()).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PatientResponse> update(@PathVariable Long id,
            @Valid @RequestBody UpdatePatientRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(patients.update(id, body));
    }
}
