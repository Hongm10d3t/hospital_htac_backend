package com.htac_backend.modules.administration.controller;

import com.htac_backend.modules.administration.dto.CreateStaffAccountRequest;
import com.htac_backend.modules.administration.dto.StaffAccountResponse;
import com.htac_backend.modules.administration.dto.UpdateStaffAccountRequest;
import com.htac_backend.modules.administration.service.StaffAccountService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/staff-accounts")
public class StaffAccountController {
    private static final Set<String> SORT_FIELDS = Set.of("id", "username", "fullName", "role", "active");
    private final StaffAccountService staff;

    public StaffAccountController(StaffAccountService staff) {
        this.staff = staff;
    }

    @GetMapping
    public ResponseEntity<PageResponse<StaffAccountResponse>> list(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(staff.list(PageQuery.parse(page, size, sort, "id", SORT_FIELDS)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StaffAccountResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(staff.get(id));
    }

    @PostMapping
    public ResponseEntity<StaffAccountResponse> create(@Valid @RequestBody CreateStaffAccountRequest body) {
        StaffAccountResponse created = staff.create(body);
        return ResponseEntity.created(URI.create("/api/v1/admin/staff-accounts/" + created.id()))
                .cacheControl(CacheControl.noStore()).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<StaffAccountResponse> update(@PathVariable Long id,
            @Valid @RequestBody UpdateStaffAccountRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(staff.update(id, body));
    }

    @PostMapping("/{id}/lock")
    public ResponseEntity<Void> lock(@PathVariable Long id) {
        staff.setActive(id, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<Void> unlock(@PathVariable Long id) {
        staff.setActive(id, true);
        return ResponseEntity.noContent().build();
    }
}
