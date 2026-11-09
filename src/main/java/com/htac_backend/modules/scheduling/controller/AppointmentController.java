package com.htac_backend.modules.scheduling.controller;

import com.htac_backend.modules.scheduling.dto.AppointmentResponse;
import com.htac_backend.modules.scheduling.dto.BookAppointmentRequest;
import com.htac_backend.modules.scheduling.service.AppointmentBookingService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {
    private final AppointmentBookingService booking;

    public AppointmentController(AppointmentBookingService booking) {
        this.booking = booking;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody BookAppointmentRequest request) {
        AppointmentResponse created = booking.book(request);
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + created.id()))
                .cacheControl(CacheControl.noStore()).body(created);
    }
}
