package com.htac_backend.modules.clinicdirectory.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.clinicdirectory.dto.DoctorProfileAdminResponse;
import com.htac_backend.modules.clinicdirectory.service.DoctorProfileAdminService;
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

@WebMvcTest(DoctorProfileAdminController.class)
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class DoctorProfileAdminApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean DoctorProfileAdminService profiles;

    @Test
    void onlyAdminCanManageProfiles() throws Exception {
        mvc.perform(get("/api/v1/admin/doctor-profiles"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/doctor-profiles").with(user("doctor").roles("DOCTOR")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(profiles);
    }

    @Test
    void adminCanCreateAndInputIsValidated() throws Exception {
        when(profiles.create(any())).thenReturn(new DoctorProfileAdminResponse(42L, 5L, null,
                "MD", "Consultant", (short) 2010, null, null, null, false));
        mvc.perform(post("/api/v1/admin/doctor-profiles").with(user("admin").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":42,\"specialtyId\":5,\"professionalTitle\":\"MD\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/doctor-profiles/42"))
                .andExpect(jsonPath("$.position").value("Consultant"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());
        mvc.perform(post("/api/v1/admin/doctor-profiles").with(user("admin").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":0,\"specialtyId\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
