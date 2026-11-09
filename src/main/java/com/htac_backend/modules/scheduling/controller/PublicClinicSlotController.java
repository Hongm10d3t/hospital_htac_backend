package com.htac_backend.modules.scheduling.controller;

import com.htac_backend.modules.scheduling.dto.PublicClinicSlotResponse;
import com.htac_backend.modules.scheduling.service.PublicClinicSlotService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/clinic-slots")
public class PublicClinicSlotController {
    private final PublicClinicSlotService slots;

    public PublicClinicSlotController(PublicClinicSlotService slots) {
        this.slots = slots;
    }

    @GetMapping
    public ResponseEntity<PageResponse<PublicClinicSlotResponse>> search(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(slots.search(date, specialtyId,
                doctorId, PageQuery.parse(page, size, sort, "startAt", Set.of("startAt"))));
    }
}
