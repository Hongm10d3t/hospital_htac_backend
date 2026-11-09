package com.htac_backend.modules.clinicdirectory.controller;

import com.htac_backend.modules.clinicdirectory.dto.ClinicRoomResponse;
import com.htac_backend.modules.clinicdirectory.dto.CreateClinicRoomRequest;
import com.htac_backend.modules.clinicdirectory.dto.UpdateClinicRoomRequest;
import com.htac_backend.modules.clinicdirectory.service.ClinicRoomAdminService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/api/v1/admin/clinic-rooms")
public class ClinicRoomAdminController {
    private static final Set<String> SORT_FIELDS = Set.of("id", "code", "name", "specialtyId", "active");
    private final ClinicRoomAdminService rooms;

    public ClinicRoomAdminController(ClinicRoomAdminService rooms) {
        this.rooms = rooms;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ClinicRoomResponse>> list(
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rooms.list(specialtyId, active,
                PageQuery.parse(page, size, sort, "id", SORT_FIELDS)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClinicRoomResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rooms.get(id));
    }

    @PostMapping
    public ResponseEntity<ClinicRoomResponse> create(@Valid @RequestBody CreateClinicRoomRequest body) {
        ClinicRoomResponse created = rooms.create(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/clinic-rooms/" + created.id()))
                .cacheControl(CacheControl.noStore()).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClinicRoomResponse> update(@PathVariable Long id,
            @Valid @RequestBody UpdateClinicRoomRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(rooms.update(id, body));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        rooms.setActive(id, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        rooms.setActive(id, false);
        return ResponseEntity.noContent().build();
    }
}
