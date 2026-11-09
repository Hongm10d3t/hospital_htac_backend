package com.htac_backend.modules.scheduling.controller;

import com.htac_backend.modules.scheduling.dto.AppointmentResponse;
import com.htac_backend.modules.scheduling.dto.BookAppointmentRequest;
import com.htac_backend.modules.scheduling.service.AppointmentBookingService;
import com.htac_backend.modules.scheduling.service.PatientAppointmentService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {
    private final AppointmentBookingService booking;
    private final PatientAppointmentService own;

    public AppointmentController(AppointmentBookingService booking, PatientAppointmentService own) {
        this.booking = booking;
        this.own = own;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AppointmentResponse>> list(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(own.ownList(
                PageQuery.parse(page, size, sort, "createdAt", Set.of("id", "createdAt"))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppointmentResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(own.ownGet(id));
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody BookAppointmentRequest request) {
        AppointmentResponse created = booking.book(request);
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + created.id()))
                .cacheControl(CacheControl.noStore()).body(created);
    }
}
