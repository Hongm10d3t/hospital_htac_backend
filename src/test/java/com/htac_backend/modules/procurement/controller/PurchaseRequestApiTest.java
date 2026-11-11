package com.htac_backend.modules.procurement.controller;

import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.procurement.service.PurchaseRequestService;
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

@WebMvcTest(PurchaseRequestController.class)
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class PurchaseRequestApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean PurchaseRequestService requests;

    @Test
    void roleCsrfAndInputGateActions() throws Exception {
        String base = "/api/v1/procurement/purchase-requests";
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(get(base).with(user("doctor").roles("DOCTOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post(base).with(user("pharmacist").roles("PHARMACIST")))
                .andExpect(status().isForbidden());
        mvc.perform(post(base + "/1/approve").with(user("pharmacist").roles("PHARMACIST"))
                        .with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(base + "/1/reject").with(user("admin").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base).with(user("pharmacist").roles("PHARMACIST"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base + "/1/order").with(user("pharmacist").roles("PHARMACIST"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(requests);
    }
}
