package com.htac_backend.modules.clinicdirectory.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.clinicdirectory.dto.ClinicRoomResponse;
import com.htac_backend.modules.clinicdirectory.dto.SpecialtyResponse;
import com.htac_backend.modules.clinicdirectory.service.ClinicRoomAdminService;
import com.htac_backend.modules.clinicdirectory.service.SpecialtyAdminService;
import com.htac_backend.security.SecurityConfig;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({SpecialtyAdminController.class, ClinicRoomAdminController.class})
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class ClinicDirectoryAdminApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean SpecialtyAdminService specialties;
    @MockitoBean ClinicRoomAdminService rooms;

    @Test
    void anonymousAndOtherRolesCannotManageDirectory() throws Exception {
        mvc.perform(get("/api/v1/admin/specialties"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/clinic-rooms").with(user("doctor").roles("DOCTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/specialties").with(user("patient").roles("PATIENT"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CARD\",\"name\":\"Cardiology\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(specialties, rooms);
    }

    @Test
    void adminCanCreateAndInvalidInputIsRejected() throws Exception {
        when(specialties.create(any())).thenReturn(new SpecialtyResponse(12L, "CARD", "Cardiology", true));
        mvc.perform(post("/api/v1/admin/specialties").with(user("admin").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CARD\",\"name\":\"Cardiology\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/specialties/12"));
        mvc.perform(post("/api/v1/admin/clinic-rooms").with(user("admin").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specialtyId\":0,\"code\":\"BAD CODE\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        when(rooms.create(any())).thenReturn(new ClinicRoomResponse(20L, 12L, "R01", "Room 1", null, true));
        mvc.perform(post("/api/v1/admin/clinic-rooms").with(user("admin").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specialtyId\":12,\"code\":\"R01\",\"name\":\"Room 1\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/clinic-rooms/20"));
    }
}
