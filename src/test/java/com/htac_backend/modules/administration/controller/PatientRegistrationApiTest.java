package com.htac_backend.modules.administration.controller;

import com.htac_backend.modules.administration.dto.PatientRegistrationResponse;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.administration.service.PatientAccountRegistrationService;
import com.htac_backend.modules.reception.controller.PatientRegistrationVerificationController;
import com.htac_backend.modules.reception.dto.RegistrationVerificationResponse;
import com.htac_backend.modules.reception.service.PatientRegistrationLinkService;
import com.htac_backend.security.SecurityConfig;
import com.htac_backend.shared.api.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PatientRegistrationController.class, PatientRegistrationVerificationController.class})
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class PatientRegistrationApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean PatientAccountRegistrationService registrations;
    @MockitoBean PatientRegistrationLinkService links;

    @Test
    void publicRegistrationNeedsCsrfAndIgnoresSubmittedRole() throws Exception {
        String body = "{\"username\":\"patient_1\",\"password\":\"%s\","
                + "\"fullName\":\"Synthetic Patient\",\"phone\":\"0000000000\","
                + "\"citizenId\":\"000000000001\",\"dateOfBirth\":\"1990-01-02\","
                + "\"gender\":\"OTHER\",\"role\":\"ADMIN\"}";
        String json = body.formatted(UUID.randomUUID());
        mvc.perform(post("/api/v1/public/auth/register").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
        verifyNoInteractions(registrations);

        when(registrations.register(any())).thenReturn(new PatientRegistrationResponse(11L, 20L, "ACTIVE"));
        mvc.perform(post("/api/v1/public/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    void verificationAndCandidateSearchRequireReceptionist() throws Exception {
        String path = "/api/v1/reception/patients/10/registration-verification";
        String body = "{\"citizenId\":\"000000000001\",\"fullName\":\"Synthetic Patient\","
                + "\"dateOfBirth\":\"1990-01-02\"}";
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        for (String role : new String[] {"ADMIN", "DOCTOR", "PHARMACIST", "PATIENT"}) {
            mvc.perform(post(path).with(user("other").roles(role)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(links);

        when(links.issueVerification(eq(10L), any())).thenReturn(
                new RegistrationVerificationResponse(UUID.randomUUID().toString(), Instant.now().plusSeconds(900)));
        mvc.perform(post(path).with(user("reception").roles("RECEPTIONIST")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(get("/api/v1/reception/patients/registration-candidates")
                        .param("citizenId", "000000000001"))
                .andExpect(status().isUnauthorized());
        when(links.candidates(any(), any(), any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/v1/reception/patients/registration-candidates")
                        .with(user("reception").roles("RECEPTIONIST"))
                        .param("fullName", "Synthetic Patient").param("dateOfBirth", "1990-01-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }
}
