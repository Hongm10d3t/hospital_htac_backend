package com.htac_backend.modules.reception.controller;

import com.htac_backend.modules.reception.dto.IssueRegistrationVerificationRequest;
import com.htac_backend.modules.reception.dto.RegistrationVerificationResponse;
import com.htac_backend.modules.reception.dto.PatientRegistrationCandidateResponse;
import com.htac_backend.modules.reception.service.PatientRegistrationLinkService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reception/patients")
public class PatientRegistrationVerificationController {
    private final PatientRegistrationLinkService links;

    public PatientRegistrationVerificationController(PatientRegistrationLinkService links) {
        this.links = links;
    }

    @GetMapping("/registration-candidates")
    public ResponseEntity<PageResponse<PatientRegistrationCandidateResponse>> candidates(
            @RequestParam(required = false) String citizenId,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) LocalDate dateOfBirth,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(links.candidates(
                citizenId, fullName, dateOfBirth, PageQuery.parse(page, size, null, "id", Set.of("id"))));
    }

    @PostMapping("/{id}/registration-verification")
    public ResponseEntity<RegistrationVerificationResponse> issue(@PathVariable Long id,
            @Valid @RequestBody IssueRegistrationVerificationRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(links.issueVerification(id, request));
    }
}
