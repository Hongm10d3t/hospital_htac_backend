package com.htac_backend.modules.scheduling.controller;

import com.htac_backend.modules.scheduling.dto.NotCompleteAppointmentRequest;
import com.htac_backend.modules.scheduling.dto.ReceptionAppointmentResponse;
import com.htac_backend.modules.scheduling.entity.AppointmentStatus;
import com.htac_backend.modules.scheduling.service.ReceptionAppointmentService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reception/appointments")
public class ReceptionAppointmentController {
    private final ReceptionAppointmentService appointments;

    public ReceptionAppointmentController(ReceptionAppointmentService appointments) {
        this.appointments = appointments;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ReceptionAppointmentResponse>> list(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(appointments.list(status, date,
                PageQuery.parse(page, size, sort, "createdAt", Set.of("id", "createdAt"))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReceptionAppointmentResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(appointments.get(id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ReceptionAppointmentResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(appointments.confirm(id));
    }

    @PostMapping("/{id}/not-complete")
    public ResponseEntity<ReceptionAppointmentResponse> notComplete(@PathVariable Long id,
            @Valid @RequestBody NotCompleteAppointmentRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(appointments.notComplete(id, body.reason()));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ReceptionAppointmentResponse> complete(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(appointments.complete(id));
    }
}
