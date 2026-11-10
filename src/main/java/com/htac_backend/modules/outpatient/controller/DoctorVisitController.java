package com.htac_backend.modules.outpatient.controller;

import com.htac_backend.modules.outpatient.dto.DoctorVisitResponse;
import com.htac_backend.modules.outpatient.dto.PatientVisitDetail;
import com.htac_backend.modules.outpatient.dto.PatientVisitSummary;
import com.htac_backend.modules.outpatient.service.DoctorVisitService;
import com.htac_backend.modules.scheduling.dto.ClinicSlotResponse;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/doctor")
public class DoctorVisitController {
    private final DoctorVisitService visits;

    public DoctorVisitController(DoctorVisitService visits) {
        this.visits = visits;
    }

    @GetMapping("/slots")
    public PageResponse<ClinicSlotResponse> slots(@RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return visits.slots(date, PageQuery.parse(page, size, sort == null ? "startAt,ASC" : sort,
                "startAt", Set.of("startAt", "id")));
    }

    @GetMapping("/slots/{slotId}/waiting")
    public PageResponse<DoctorVisitResponse> waiting(@PathVariable Long slotId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return visits.waiting(slotId, PageQuery.parse(page, size,
                sort == null ? "queueNumber,ASC" : sort, "queueNumber", Set.of("queueNumber", "id")));
    }

    @GetMapping("/visits/{visitId}")
    public DoctorVisitResponse get(@PathVariable Long visitId) {
        return visits.get(visitId);
    }

    @PostMapping("/visits/{visitId}/start")
    public DoctorVisitResponse start(@PathVariable Long visitId) {
        return visits.start(visitId);
    }

    @GetMapping("/visits/{visitId}/history")
    public PageResponse<PatientVisitSummary> history(@PathVariable Long visitId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return visits.patientHistory(visitId, PageQuery.parse(page, size,
                sort == null ? "arrivedAt,DESC" : sort, "arrivedAt", Set.of("arrivedAt", "id")));
    }

    @GetMapping("/visits/{visitId}/history/{pastVisitId}")
    public PatientVisitDetail historyDetail(@PathVariable Long visitId, @PathVariable Long pastVisitId) {
        return visits.patientHistoryDetail(visitId, pastVisitId);
    }
}
