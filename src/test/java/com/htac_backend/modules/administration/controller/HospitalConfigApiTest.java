package com.htac_backend.modules.administration.controller;

import com.htac_backend.modules.administration.dto.HospitalConfigResponse;
import com.htac_backend.modules.administration.dto.HospitalConfigWriteResult;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.administration.service.HospitalConfigService;
import com.htac_backend.security.SecurityConfig;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HospitalConfigController.class)
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class HospitalConfigApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean HospitalConfigService configs;

    @Test
    void publicReadIsOpenAndAdminWriteIsProtected() throws Exception {
        when(configs.publicInfo()).thenReturn(response(7));
        mvc.perform(get("/api/v1/public/hospital"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advanceBookingDays").value(7));

        mvc.perform(put("/api/v1/admin/hospital-config").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(7)))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/admin/hospital-config").with(user("patient").roles("PATIENT"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(7)))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verify(configs).publicInfo();

        when(configs.upsert(any())).thenReturn(new HospitalConfigWriteResult(response(7), true));
        mvc.perform(put("/api/v1/admin/hospital-config").with(user("admin").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(7)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/public/hospital"));
    }

    @Test
    void invalidBookingWindowIsRejectedBeforeWrite() throws Exception {
        mvc.perform(put("/api/v1/admin/hospital-config").with(user("admin").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(configs);
    }

    private String body(int days) {
        return "{\"name\":\"Synthetic Hospital\",\"address\":\"Synthetic Address\","
                + "\"phone\":\"0000000000\",\"advanceBookingDays\":" + days + "}";
    }

    private HospitalConfigResponse response(int days) {
        return new HospitalConfigResponse("Synthetic Hospital", "Synthetic Address",
                "0000000000", null, null, null, days);
    }
}
