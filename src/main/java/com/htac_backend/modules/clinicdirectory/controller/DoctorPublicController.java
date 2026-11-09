package com.htac_backend.modules.clinicdirectory.controller;

import com.htac_backend.modules.clinicdirectory.dto.DoctorPublicResponse;
import com.htac_backend.modules.clinicdirectory.service.DoctorPublicService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/doctors")
public class DoctorPublicController {
    private static final Set<String> SORT_FIELDS = Set.of("userId", "practiceStartYear", "professionalTitle");
    private final DoctorPublicService doctors;

    public DoctorPublicController(DoctorPublicService doctors) {
        this.doctors = doctors;
    }

    @GetMapping
    public ResponseEntity<PageResponse<DoctorPublicResponse>> list(
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(doctors.list(specialtyId,
                PageQuery.parse(page, size, sort, "userId", SORT_FIELDS)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<DoctorPublicResponse> get(@PathVariable Long userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(doctors.get(userId));
    }
}
