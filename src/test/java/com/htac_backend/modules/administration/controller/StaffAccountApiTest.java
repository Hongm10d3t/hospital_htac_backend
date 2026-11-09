package com.htac_backend.modules.administration.controller;

import com.htac_backend.modules.administration.dto.StaffAccountResponse;
import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.administration.service.StaffAccountService;
import com.htac_backend.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffAccountController.class)
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class StaffAccountApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean StaffAccountService staff;

    @Test
    void anonymousAndOtherRolesCannotAccessStaffManagement() throws Exception {
        mvc.perform(get("/api/v1/admin/staff-accounts"))
                .andExpect(status().isUnauthorized());
        for (String role : new String[] {"RECEPTIONIST", "DOCTOR", "PHARMACIST", "PATIENT"}) {
            mvc.perform(get("/api/v1/admin/staff-accounts").with(
                    org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                            .user("other").roles(role)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(staff);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminGetsCreatedAccountWithoutPasswordFields() throws Exception {
        when(staff.create(any())).thenReturn(new StaffAccountResponse(12L, "staff_12", "Staff", "0000000000",
                AccountRole.RECEPTIONIST, true));
        mvc.perform(post("/api/v1/admin/staff-accounts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"staff_12\",\"password\":\"${GENERATED_PASSWORD}\","
                                + "\"fullName\":\"Staff\",\"phone\":\"0000000000\","
                                + "\"role\":\"RECEPTIONIST\"}".replace("${GENERATED_PASSWORD}",
                                        java.util.UUID.randomUUID().toString())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/staff-accounts/12"))
                .andExpect(jsonPath("$.role").value("RECEPTIONIST"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidRequestIsRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/v1/admin/staff-accounts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bad user\","
                                + "\"fullName\":\"\",\"phone\":\"\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(staff);
    }
}
