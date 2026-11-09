package com.htac_backend.modules.clinicdirectory.controller;

import com.htac_backend.modules.clinicdirectory.dto.CreateDoctorProfileRequest;
import com.htac_backend.modules.clinicdirectory.dto.DoctorProfileAdminResponse;
import com.htac_backend.modules.clinicdirectory.dto.UpdateDoctorProfileRequest;
import com.htac_backend.modules.clinicdirectory.service.DoctorProfileAdminService;
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
@RequestMapping("/api/v1/admin/doctor-profiles")
public class DoctorProfileAdminController {
    private static final Set<String> SORT_FIELDS = Set.of("userId", "specialtyId", "practiceStartYear");
    private final DoctorProfileAdminService profiles;

    public DoctorProfileAdminController(DoctorProfileAdminService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ResponseEntity<PageResponse<DoctorProfileAdminResponse>> list(
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.list(specialtyId,
                PageQuery.parse(page, size, sort, "userId", SORT_FIELDS)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<DoctorProfileAdminResponse> get(@PathVariable Long userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.get(userId));
    }

    @PostMapping
    public ResponseEntity<DoctorProfileAdminResponse> create(@Valid @RequestBody CreateDoctorProfileRequest body) {
        DoctorProfileAdminResponse created = profiles.create(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/doctor-profiles/" + created.userId()))
                .cacheControl(CacheControl.noStore()).body(created);
    }

    @PutMapping("/{userId}")
    public ResponseEntity<DoctorProfileAdminResponse> update(@PathVariable Long userId,
            @Valid @RequestBody UpdateDoctorProfileRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profiles.update(userId, body));
    }
}
