package com.htac_backend.modules.clinicdirectory.controller;

import com.htac_backend.modules.clinicdirectory.dto.CreateSpecialtyRequest;
import com.htac_backend.modules.clinicdirectory.dto.SpecialtyResponse;
import com.htac_backend.modules.clinicdirectory.dto.UpdateSpecialtyRequest;
import com.htac_backend.modules.clinicdirectory.service.SpecialtyAdminService;
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
@RequestMapping("/api/v1/admin/specialties")
public class SpecialtyAdminController {
    private static final Set<String> SORT_FIELDS = Set.of("id", "code", "name", "active");
    private final SpecialtyAdminService specialties;

    public SpecialtyAdminController(SpecialtyAdminService specialties) {
        this.specialties = specialties;
    }

    @GetMapping
    public ResponseEntity<PageResponse<SpecialtyResponse>> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(specialties.list(active,
                PageQuery.parse(page, size, sort, "id", SORT_FIELDS)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpecialtyResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(specialties.get(id));
    }

    @PostMapping
    public ResponseEntity<SpecialtyResponse> create(@Valid @RequestBody CreateSpecialtyRequest body) {
        SpecialtyResponse created = specialties.create(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/specialties/" + created.id()))
                .cacheControl(CacheControl.noStore()).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SpecialtyResponse> update(@PathVariable Long id,
            @Valid @RequestBody UpdateSpecialtyRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(specialties.update(id, body));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        specialties.setActive(id, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        specialties.setActive(id, false);
        return ResponseEntity.noContent().build();
    }
}
