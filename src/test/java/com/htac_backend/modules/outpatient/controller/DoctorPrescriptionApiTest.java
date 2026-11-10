package com.htac_backend.modules.outpatient.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.outpatient.service.DoctorPrescriptionService;
import com.htac_backend.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DoctorPrescriptionController.class)
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class DoctorPrescriptionApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean DoctorPrescriptionService prescriptions;

    @Test
    void onlyDoctorWithCsrfCanWriteAndInvalidItemsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/doctor/prescriptions/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/doctor/prescriptions/1")
                        .with(user("patient").roles("PATIENT")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/doctor/visits/1/prescriptions")
                        .with(user("doctor").roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/doctor/visits/1/prescriptions")
                        .with(user("doctor").roles("DOCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/doctor/visits/1/prescriptions")
                        .with(user("doctor").roles("DOCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"medicineId\":1,\"quantity\":0,\"usageInstruction\":\"\"}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(prescriptions);
    }
}
