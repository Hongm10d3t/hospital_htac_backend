package com.htac_backend.modules.reception.controller;

import com.htac_backend.modules.outpatient.dto.ReceptionVisit;
import com.htac_backend.modules.reception.dto.DirectReceptionRequest;
import com.htac_backend.modules.reception.dto.ReceptionOutcomeRequest;
import com.htac_backend.modules.reception.service.ReceptionOutcomeService;
import com.htac_backend.modules.reception.service.ReceptionService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reception/visits")
public class ReceptionVisitController {
    private final ReceptionService reception;
    private final ReceptionOutcomeService outcomes;

    public ReceptionVisitController(ReceptionService reception, ReceptionOutcomeService outcomes) {
        this.reception = reception;
        this.outcomes = outcomes;
    }

    @PostMapping("/appointments/{appointmentId}")
    public ReceptionVisit booked(@PathVariable Long appointmentId) {
        return reception.receiveBooked(appointmentId);
    }

    @PostMapping("/direct")
    @ResponseStatus(HttpStatus.CREATED)
    public ReceptionVisit direct(@Valid @RequestBody DirectReceptionRequest request) {
        return reception.receiveDirect(request);
    }

    @GetMapping("/waiting")
    public PageResponse<ReceptionVisit> waiting(@RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Long roomId, @RequestParam(required = false) Long slotId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return reception.waiting(date, roomId, slotId,
                PageQuery.parse(page, size, sort, "queueNumber", Set.of("queueNumber", "id")));
    }

    @PostMapping("/{visitId}/not-complete")
    public ReceptionVisit notComplete(@PathVariable Long visitId,
            @Valid @RequestBody ReceptionOutcomeRequest request) {
        return outcomes.notComplete(visitId, request.reason());
    }

    @PostMapping("/{visitId}/void")
    public ReceptionVisit voidReception(@PathVariable Long visitId,
            @Valid @RequestBody ReceptionOutcomeRequest request) {
        return outcomes.voidReception(visitId, request.reason());
    }
}
