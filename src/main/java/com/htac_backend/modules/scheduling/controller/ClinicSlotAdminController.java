package com.htac_backend.modules.scheduling.controller;

import com.htac_backend.modules.scheduling.dto.ClinicSlotResponse;
import com.htac_backend.modules.scheduling.dto.CreateClinicSessionRequest;
import com.htac_backend.modules.scheduling.dto.CreateClinicSlotRequest;
import com.htac_backend.modules.scheduling.dto.UpdateClinicSlotRequest;
import com.htac_backend.modules.scheduling.service.ClinicSlotAdminService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/clinic-slots")
public class ClinicSlotAdminController {
    private static final Set<String> SORT_FIELDS = Set.of("id", "startAt", "endAt", "capacity");
    private final ClinicSlotAdminService slots;

    public ClinicSlotAdminController(ClinicSlotAdminService slots) {
        this.slots = slots;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ClinicSlotResponse>> list(
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) java.time.OffsetDateTime from,
            @RequestParam(required = false) java.time.OffsetDateTime to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(slots.list(roomId, doctorId,
                from == null ? null : from.toInstant(), to == null ? null : to.toInstant(),
                PageQuery.parse(page, size, sort, "startAt", SORT_FIELDS)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClinicSlotResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(slots.get(id));
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

    @PutMapping("/{id}")
    public ResponseEntity<ClinicSlotResponse> update(@PathVariable Long id,
            @Valid @RequestBody UpdateClinicSlotRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(slots.update(id, body));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(@PathVariable Long id) {
        slots.close(id);
        return ResponseEntity.noContent().build();
    }
}
