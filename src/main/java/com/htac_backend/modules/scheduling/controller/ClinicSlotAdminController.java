package com.htac_backend.modules.scheduling.controller;

import com.htac_backend.modules.scheduling.dto.ClinicSlotResponse;
import com.htac_backend.modules.scheduling.dto.CreateClinicSessionRequest;
import com.htac_backend.modules.scheduling.dto.CreateClinicSlotRequest;
import com.htac_backend.modules.scheduling.service.ClinicSlotAdminService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/clinic-slots")
public class ClinicSlotAdminController {
    private final ClinicSlotAdminService slots;

    public ClinicSlotAdminController(ClinicSlotAdminService slots) {
        this.slots = slots;
    }

    @PostMapping
    public ResponseEntity<ClinicSlotResponse> create(@Valid @RequestBody CreateClinicSlotRequest body) {
        ClinicSlotResponse created = slots.create(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/clinic-slots/" + created.id()))
                .cacheControl(CacheControl.noStore()).body(created);
    }

    @PostMapping("/sessions")
    public ResponseEntity<List<ClinicSlotResponse>> createSession(
            @Valid @RequestBody CreateClinicSessionRequest body) {
        List<ClinicSlotResponse> created = slots.createSession(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/clinic-slots/" + created.getFirst().id()))
                .cacheControl(CacheControl.noStore()).body(created);
    }
}
