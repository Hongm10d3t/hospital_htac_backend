package com.htac_backend.modules.outpatient.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.outpatient.service.DoctorVisitService;
import com.htac_backend.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DoctorVisitController.class)
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class DoctorVisitApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean DoctorVisitService visits;

    @Test
    void doctorRoleAndCsrfAreRequired() throws Exception {
        mvc.perform(get("/api/v1/doctor/slots")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/doctor/slots").with(user("patient").roles("PATIENT")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/doctor/visits/1/start")
                        .with(user("doctor").roles("DOCTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/doctor/slots?size=101")
                        .with(user("doctor").roles("DOCTOR")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(visits);
    }
}
