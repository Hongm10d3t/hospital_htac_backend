package com.htac_backend.modules.pharmacy.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.pharmacy.service.MedicineCatalogService;
import com.htac_backend.modules.pharmacy.service.MedicineSelectionService;
import com.htac_backend.modules.pharmacy.service.SupplierCatalogService;
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

@WebMvcTest({MedicineCatalogController.class, SupplierCatalogController.class,
        DoctorMedicineSelectionController.class})
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class MedicineCatalogApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean MedicineCatalogService medicines;
    @MockitoBean SupplierCatalogService suppliers;
    @MockitoBean MedicineSelectionService selection;

    @Test
    void pharmacyCatalogRequiresRoleAndCsrf() throws Exception {
        mvc.perform(get("/api/v1/pharmacy/medicines")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/pharmacy/medicines")
                        .with(user("doctor").roles("DOCTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/pharmacy/medicines")
                        .with(user("pharmacist").roles("PHARMACIST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/pharmacy/medicines")
                        .with(user("pharmacist").roles("PHARMACIST")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/doctor/medicines")
                        .with(user("pharmacist").roles("PHARMACIST")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(medicines, suppliers, selection);
    }
}
