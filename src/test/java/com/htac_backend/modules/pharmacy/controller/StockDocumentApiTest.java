package com.htac_backend.modules.pharmacy.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.pharmacy.service.StockDocumentService;
import com.htac_backend.modules.pharmacy.service.InboundStockService;
import com.htac_backend.modules.pharmacy.service.TransferStockService;
import com.htac_backend.modules.pharmacy.service.DispenseStockService;
import com.htac_backend.modules.pharmacy.service.UnfilledReviewService;
import com.htac_backend.modules.pharmacy.service.PatientReturnService;
import com.htac_backend.modules.pharmacy.service.StockCorrectionService;
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

@WebMvcTest({StockDocumentController.class, PharmacyPrescriptionController.class})
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class StockDocumentApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean StockDocumentService documents;
    @MockitoBean InboundStockService inbound;
    @MockitoBean TransferStockService transfers;
    @MockitoBean DispenseStockService dispensing;
    @MockitoBean UnfilledReviewService reviews;
    @MockitoBean PatientReturnService patientReturns;
    @MockitoBean StockCorrectionService corrections;

    @Test
    void postingRequiresPharmacistAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/pharmacy/stock-documents/1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/pharmacy/stock-documents/1")
                        .with(user("doctor").roles("DOCTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/pharmacy/stock-documents/1/post")
                        .with(user("pharmacist").roles("PHARMACIST")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/pharmacy/stock-documents/1/cancel")
                        .with(user("pharmacist").roles("PHARMACIST")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/pharmacy/stock-documents/receipts")
                        .with(user("doctor").roles("DOCTOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/pharmacy/stock-documents/openings")
                        .with(user("pharmacist").roles("PHARMACIST")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/pharmacy/stock-documents/transfers")
                        .with(user("pharmacist").roles("PHARMACIST")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/pharmacy/stock-documents/dispensations")
                        .with(user("pharmacist").roles("PHARMACIST")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/pharmacy/prescriptions/1/dispensing")
                        .with(user("doctor").roles("DOCTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/pharmacy/prescriptions/1/dispensing-review")
                        .with(user("pharmacist").roles("PHARMACIST")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/pharmacy/stock-documents/patient-returns")
                        .with(user("pharmacist").roles("PHARMACIST")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/pharmacy/stock-documents/adjustments")
                        .with(user("pharmacist").roles("PHARMACIST")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(documents, inbound, transfers, dispensing, reviews, patientReturns,
                corrections);
    }
}
