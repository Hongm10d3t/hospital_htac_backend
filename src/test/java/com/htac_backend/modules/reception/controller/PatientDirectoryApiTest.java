package com.htac_backend.modules.reception.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.reception.dto.PatientResponse;
import com.htac_backend.modules.reception.entity.PatientGender;
import com.htac_backend.modules.reception.service.PatientDirectoryService;
import com.htac_backend.security.SecurityConfig;
import java.time.LocalDate;
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

@WebMvcTest({PatientDirectoryController.class, PatientSelfController.class})
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class PatientDirectoryApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean PatientDirectoryService patients;

    @Test
    void receptionEndpointsRequireRoleAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/reception/patients?patientCode=P1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/reception/patients?patientCode=P1")
                        .with(user("patient").roles("PATIENT")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reception/patients").with(user("reception").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(patients);
    }

    @Test
    void directProfileCreatedWithoutPhoneAndInvalidBodyRejected() throws Exception {
        when(patients.create(any())).thenReturn(new PatientResponse(7L, "P0007", null, null,
                "Test Patient", LocalDate.of(1990, 1, 1), PatientGender.OTHER, null));
        mvc.perform(post("/api/v1/reception/patients")
                        .with(user("reception").roles("RECEPTIONIST")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Test Patient\",\"dateOfBirth\":\"1990-01-01\",\"gender\":\"OTHER\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/reception/patients/7"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.userId").isEmpty());
        mvc.perform(post("/api/v1/reception/patients")
                        .with(user("reception").roles("RECEPTIONIST")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"dateOfBirth\":\"2030-01-01\",\"gender\":\"OTHER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void patientSelfEndpointDoesNotExposeOtherPatientIdSelector() throws Exception {
        mvc.perform(get("/api/v1/patients/me"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/patients/me").with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isForbidden());
        when(patients.own()).thenReturn(new PatientResponse(7L, "P0007", 11L, null,
                "Test Patient", LocalDate.of(1990, 1, 1), PatientGender.OTHER, null));
        mvc.perform(get("/api/v1/patients/me").with(user("patient").roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }
}
