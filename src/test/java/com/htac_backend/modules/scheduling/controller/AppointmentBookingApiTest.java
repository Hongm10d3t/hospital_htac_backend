package com.htac_backend.modules.scheduling.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.scheduling.dto.AppointmentResponse;
import com.htac_backend.modules.scheduling.dto.PublicClinicSlotResponse;
import com.htac_backend.modules.scheduling.entity.AppointmentStatus;
import com.htac_backend.modules.scheduling.service.AppointmentBookingService;
import com.htac_backend.modules.scheduling.service.PublicClinicSlotService;
import com.htac_backend.modules.scheduling.service.PatientAppointmentService;
import com.htac_backend.security.SecurityConfig;
import com.htac_backend.shared.api.PageResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AppointmentController.class, PublicClinicSlotController.class})
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class AppointmentBookingApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean AppointmentBookingService booking;
    @MockitoBean PublicClinicSlotService publicSlots;
    @MockitoBean PatientAppointmentService own;

    @Test
    void bookingRequiresPatientAndDoesNotAcceptPatientIdAsAuthority() throws Exception {
        String request = "{\"slotId\":12,\"reason\":\"Checkup\",\"patientId\":999}";
        mvc.perform(post("/api/v1/appointments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/appointments").with(user("doctor").roles("DOCTOR"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        verifyNoInteractions(booking);
        when(booking.book(any())).thenReturn(new AppointmentResponse(17L, "A123", 12L, 3L, 2L,
                Instant.parse("2030-01-01T01:00:00Z"), Instant.parse("2030-01-01T01:15:00Z"),
                AppointmentStatus.PENDING, null, null, null));
        mvc.perform(post("/api/v1/appointments").with(user("patient").roles("PATIENT"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/appointments/17"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.patientId").doesNotExist());
    }

    @Test
    void anonymousCanSearchAvailableSlotsWithoutPatientData() throws Exception {
        var slot = new PublicClinicSlotResponse(12L, 2L, "Room", 3L, 4L, "Cardiology",
                Instant.parse("2030-01-01T01:00:00Z"), Instant.parse("2030-01-01T01:15:00Z"), 2, 1);
        when(publicSlots.search(isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageResponse<>(List.of(slot), 0, 20, 1, 1));
        mvc.perform(get("/api/v1/public/clinic-slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].available").value(1))
                .andExpect(jsonPath("$.items[0].patientId").doesNotExist())
                .andExpect(jsonPath("$.items[0].phone").doesNotExist());
    }

    @Test
    void onlyPatientCanReadOwnAppointmentsAndResponseHasNoContactPhone() throws Exception {
        mvc.perform(get("/api/v1/appointments/17"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/appointments/17").with(user("doctor").roles("DOCTOR")))
                .andExpect(status().isForbidden());
        when(own.ownGet(17L)).thenReturn(new AppointmentResponse(17L, "A123", 12L, 3L, 2L,
                Instant.parse("2030-01-01T01:00:00Z"), Instant.parse("2030-01-01T01:15:00Z"),
                AppointmentStatus.PENDING, null, null, null));
        mvc.perform(get("/api/v1/appointments/17").with(user("patient").roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }
}
