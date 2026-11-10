package com.htac_backend.modules.reception.controller;

import com.htac_backend.modules.outpatient.dto.PatientPrescriptionDetail;
import com.htac_backend.modules.outpatient.dto.PatientPrescriptionSummary;
import com.htac_backend.modules.outpatient.dto.PatientVisitDetail;
import com.htac_backend.modules.outpatient.dto.PatientVisitSummary;
import com.htac_backend.modules.reception.service.PatientHistoryService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients/me")
public class PatientHistoryController {
    private final PatientHistoryService history;

    public PatientHistoryController(PatientHistoryService history) {
        this.history = history;
    }

    @GetMapping("/visits")
    public ResponseEntity<PageResponse<PatientVisitSummary>> visits(
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.visits(from, to,
                PageQuery.parse(page, size, sort == null ? "arrivedAt,DESC" : sort,
                        "arrivedAt", Set.of("arrivedAt", "id"))));
    }

    @GetMapping("/visits/{visitId}")
    public ResponseEntity<PatientVisitDetail> visit(@PathVariable Long visitId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.visit(visitId));
    }

    @GetMapping("/prescriptions")
    public ResponseEntity<PageResponse<PatientPrescriptionSummary>> prescriptions(
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.prescriptions(from, to,
                PageQuery.parse(page, size, sort == null ? "issuedAt,DESC" : sort,
                        "issuedAt", Set.of("issuedAt", "id"))));
    }

    @GetMapping("/prescriptions/{prescriptionId}")
    public ResponseEntity<PatientPrescriptionDetail> prescription(@PathVariable Long prescriptionId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(history.prescription(prescriptionId));
    }
}
