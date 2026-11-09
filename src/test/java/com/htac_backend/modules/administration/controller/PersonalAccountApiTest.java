package com.htac_backend.modules.administration.controller;

import com.htac_backend.modules.administration.dto.AccountResponse;
import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.modules.administration.service.PersonalAccountService;
import com.htac_backend.security.SecurityConfig;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonalAccountController.class)
@Import({SecurityConfig.class, DatabaseUserDetailsService.class})
class PersonalAccountApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserAccountRepository accounts;
    @MockitoBean PersonalAccountService personal;

    @Test
    void personalUpdateRequiresAuthenticationAndCsrf() throws Exception {
        String path = "/api/v1/auth/me";
        mvc.perform(patch(path).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(path).with(user("patient").roles("PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(personal);

        when(personal.updateOwn(any())).thenReturn(new AccountResponse(1L, "patient", "New Name",
                "0000000000", AccountRole.PATIENT));
        mvc.perform(patch(path).with(user("patient").roles("PATIENT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void onlyAdminCanResetAndResponseHasNoPassword() throws Exception {
        String body = "{\"newPassword\":\"%s\",\"verifiedVia\":\"IN_PERSON\"}"
                .formatted(UUID.randomUUID());
        String path = "/api/v1/admin/accounts/12/password-reset";
        mvc.perform(post(path).with(user("patient").roles("PATIENT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verifyNoInteractions(personal);
        mvc.perform(post(path).with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        org.mockito.Mockito.verify(personal).resetPassword(eq(12L), any());
    }

    @Test
    void authenticatedUserCanChangePasswordWithCsrf() throws Exception {
        String body = "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}"
                .formatted(UUID.randomUUID(), UUID.randomUUID());
        mvc.perform(post("/api/v1/auth/change-password").with(user("doctor").roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/change-password").with(user("doctor").roles("DOCTOR"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
    }
}
