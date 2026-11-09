package com.htac_backend.modules.scheduling.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.scheduling.dto.ClinicSlotResponse;
import com.htac_backend.modules.scheduling.entity.ClinicSlotStatus;
import com.htac_backend.modules.scheduling.service.ClinicSlotAdminService;
import com.htac_backend.security.SecurityConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClinicSlotAdminController.class)
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class ClinicSlotAdminApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean ClinicSlotAdminService slots;

    @Test
    void onlyAdminCanCreateSlots() throws Exception {
        mvc.perform(post("/api/v1/admin/clinic-slots").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/clinic-slots").with(user("patient").roles("PATIENT"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(slots);
    }

    @Test
    void adminCreatesSlotAndInvalidRequestIsRejected() throws Exception {
        when(slots.create(any())).thenReturn(new ClinicSlotResponse(11L, 1L, 2L,
                Instant.parse("2030-01-01T01:00:00Z"), Instant.parse("2030-01-01T01:15:00Z"),
                2, ClinicSlotStatus.OPEN));
        mvc.perform(post("/api/v1/admin/clinic-slots").with(user("admin").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":1,\"doctorId\":2,\"startAt\":\"2030-01-01T08:00:00+07:00\",\"endAt\":\"2030-01-01T08:15:00+07:00\",\"capacity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/clinic-slots/11"))
                .andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(post("/api/v1/admin/clinic-slots").with(user("admin").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":0,\"doctorId\":2,\"capacity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updateAndCloseRequireAdminAndCsrf() throws Exception {
        mvc.perform(put("/api/v1/admin/clinic-slots/11")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/clinic-slots/11/close")
                        .with(user("doctor").roles("DOCTOR")).with(csrf()))
                .andExpect(status().isForbidden());
        when(slots.update(any(), any())).thenReturn(new ClinicSlotResponse(11L, 1L, 2L,
                Instant.parse("2030-01-01T01:00:00Z"), Instant.parse("2030-01-01T01:15:00Z"),
                3, ClinicSlotStatus.OPEN));
        mvc.perform(put("/api/v1/admin/clinic-slots/11")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"2030-01-01T08:00:00+07:00\",\"endAt\":\"2030-01-01T08:15:00+07:00\",\"capacity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(3));
        mvc.perform(post("/api/v1/admin/clinic-slots/11/close")
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
    }
}
