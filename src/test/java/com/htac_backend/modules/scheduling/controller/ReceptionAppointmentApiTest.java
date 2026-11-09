package com.htac_backend.modules.scheduling.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.scheduling.dto.AppointmentResponse;
import com.htac_backend.modules.scheduling.dto.ReceptionAppointmentResponse;
import com.htac_backend.modules.scheduling.entity.AppointmentStatus;
import com.htac_backend.modules.scheduling.service.ReceptionAppointmentService;
import com.htac_backend.security.SecurityConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReceptionAppointmentController.class)
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class ReceptionAppointmentApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean ReceptionAppointmentService appointments;

    @Test
    void onlyReceptionistSeesContactAndCanConfirm() throws Exception {
        mvc.perform(get("/api/v1/reception/appointments/5"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/reception/appointments/5").with(user("patient").roles("PATIENT")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reception/appointments/5/confirm")
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(appointments);
        var response = new ReceptionAppointmentResponse(new AppointmentResponse(5L, "A123", 12L,
                3L, 2L, Instant.parse("2030-01-01T01:00:00Z"),
                Instant.parse("2030-01-01T01:15:00Z"), AppointmentStatus.CONFIRMED,
                Instant.parse("2029-12-30T01:00:00Z"), null, null),
                7L, "Patient", "0000000000", "Checkup");
        when(appointments.confirm(5L)).thenReturn(response);
        mvc.perform(post("/api/v1/reception/appointments/5/confirm")
                        .with(user("reception").roles("RECEPTIONIST")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.phone").value("0000000000"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
