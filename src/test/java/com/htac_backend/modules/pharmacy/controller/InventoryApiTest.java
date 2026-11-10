package com.htac_backend.modules.pharmacy.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.pharmacy.service.InventoryQueryService;
import com.htac_backend.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class InventoryApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean InventoryQueryService inventory;

    @Test
    void inventoryRequiresPharmacistAndValidPaging() throws Exception {
        mvc.perform(get("/api/v1/pharmacy/inventory"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/pharmacy/inventory")
                        .with(user("doctor").roles("DOCTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/pharmacy/inventory?page=-1")
                        .with(user("pharmacist").roles("PHARMACIST")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/pharmacy/inventory/batches/1/movements?sort=secret,DESC")
                        .with(user("pharmacist").roles("PHARMACIST")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(inventory);
    }
}
