package com.htac_backend.modules.reception.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.reception.service.PatientHistoryService;
import com.htac_backend.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientHistoryController.class)
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class PatientHistoryApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean PatientHistoryService history;

    @Test
    void onlyPatientCanReadHistoryAndBadPagingIsRejected() throws Exception {
        mvc.perform(get("/api/v1/patients/me/visits")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/patients/me/visits").with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/patients/me/visits?size=101")
                        .with(user("patient").roles("PATIENT")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/patients/me/prescriptions/9")
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(history);
    }

    @Test
    void ownHistoryIsPrivateAndCannotSelectAnotherPatient() throws Exception {
        when(history.visits(isNull(), isNull(), any())).thenReturn(
                new com.htac_backend.shared.api.PageResponse<>(java.util.List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/v1/patients/me/visits?patientId=999")
                        .with(user("patient").roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
