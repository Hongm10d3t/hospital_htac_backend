package com.htac_backend.modules.reporting.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.reporting.service.ClinicalReportService;
import com.htac_backend.modules.reporting.service.PharmacyReportService;
import com.htac_backend.modules.reporting.service.PrescriptionReportService;
import com.htac_backend.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportingController.class)
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class ReportingApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean ClinicalReportService clinical;
    @MockitoBean PrescriptionReportService prescriptions;
    @MockitoBean PharmacyReportService pharmacy;

    @Test
    void readRoutesApplyRolesDatesPagingAndMethods() throws Exception {
        mvc.perform(get("/api/v1/reports/appointments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/reports/appointments").with(user("patient").roles("PATIENT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/stock-balances").with(user("doctor").roles("DOCTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/visits").with(user("pharmacist").roles("PHARMACIST")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reports/appointments").with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/appointments?sort=unsupported,ASC")
                        .with(user("doctor").roles("DOCTOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/stock-movements?page=-1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/visits?from=2026-11-11&to=2026-11-12&unknown=1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(clinical, prescriptions, pharmacy);
        mvc.perform(get("/api/v1/reports/visits?from=2026-11-11&to=2026-11-12")
                        .with(user("reception").roles("RECEPTIONIST")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports/stock-balances")
                        .with(user("pharmacist").roles("PHARMACIST")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports/prescriptions/1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
