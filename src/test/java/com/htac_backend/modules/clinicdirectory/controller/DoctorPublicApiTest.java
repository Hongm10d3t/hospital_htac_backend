package com.htac_backend.modules.clinicdirectory.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.clinicdirectory.dto.DoctorPublicResponse;
import com.htac_backend.modules.clinicdirectory.service.DoctorPublicService;
import com.htac_backend.security.SecurityConfig;
import com.htac_backend.shared.api.PageResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DoctorPublicController.class)
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class DoctorPublicApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean DoctorPublicService doctors;

    @Test
    void anonymousCanBrowseAndResponseContainsOnlyPublicFields() throws Exception {
        var doctor = new DoctorPublicResponse(42L, "Doctor Name", 5L, "Cardiology", null,
                "MD", "Consultant", (short) 2010, "Intro", "Expertise", "Certificate",
                "/api/v1/public/clinic-slots?doctorId=42");
        when(doctors.list(isNull(), any())).thenReturn(new PageResponse<>(List.of(doctor), 0, 20, 1, 1));
        mvc.perform(get("/api/v1/public/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].fullName").value("Doctor Name"))
                .andExpect(jsonPath("$.items[0].availableSlotsUrl").value(
                        "/api/v1/public/clinic-slots?doctorId=42"))
                .andExpect(jsonPath("$.items[0].username").doesNotExist())
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].phone").doesNotExist());
    }
}
