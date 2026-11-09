package com.htac_backend.modules.administration.controller;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.administration.service.AccountSessionService;
import com.htac_backend.modules.administration.service.DatabaseUserDetailsService;
import com.htac_backend.security.SecurityConfig;
import com.htac_backend.shared.api.PublicPingController;
import com.jayway.jsonpath.JsonPath;
import java.util.Optional;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, PublicPingController.class})
@Import({SecurityConfig.class, DatabaseUserDetailsService.class, AccountSessionService.class})
class AuthenticationApiTest {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean UserAccountRepository accounts;

    @Test
    void loginPersistsSessionAndReturnsOnlySafeAccountFields() throws Exception {
        UserAccount account = account(AccountRole.ADMIN, true);
        stubAccount(account);
        MvcResult csrf = csrf(null);
        MockHttpSession session = session(csrf);
        String oldSessionId = session.getId();

        MvcResult login = mvc.perform(post("/api/v1/public/auth/login").session(session)
                        .with(request -> { request.setRequestedSessionId(oldSessionId); return request; })
                        .header("X-CSRF-TOKEN", token(csrf)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        MockHttpSession authenticated = session(login);
        assertNotEquals(oldSessionId, authenticated.getId());
        SecurityContext context = (SecurityContext) authenticated.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNull(((AccountPrincipal) context.getAuthentication().getPrincipal()).getPassword());
        mvc.perform(get("/api/v1/auth/me").session(authenticated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void invalidPasswordAndDisabledAccountCannotLogin() throws Exception {
        UserAccount account = account(AccountRole.ADMIN, true);
        stubAccount(account);
        MvcResult csrf = csrf(null);
        mvc.perform(post("/api/v1/public/auth/login").session(session(csrf))
                        .header("X-CSRF-TOKEN", token(csrf)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        when(accounts.findByUsername("admin")).thenReturn(Optional.of(account(AccountRole.ADMIN, false)));
        MvcResult newCsrf = csrf(null);
        mvc.perform(post("/api/v1/public/auth/login").session(session(newCsrf))
                        .header("X-CSRF-TOKEN", token(newCsrf)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"test-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void existingSessionIsRejectedImmediatelyAfterAccountIsLocked() throws Exception {
        UserAccount account = account(AccountRole.DOCTOR, true);
        stubAccount(account);
        MockHttpSession authenticated = login();
        when(accounts.findById(1L)).thenReturn(Optional.of(account(AccountRole.DOCTOR, false)));

        mvc.perform(get("/api/v1/auth/me").session(authenticated))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertTrue(authenticated.isInvalid());
    }

    @Test
    void existingSessionIsRejectedAfterPasswordVersionChanges() throws Exception {
        UserAccount account = account(AccountRole.PHARMACIST, true);
        stubAccount(account);
        MockHttpSession authenticated = login();
        UserAccount changed = account(AccountRole.PHARMACIST, true);
        changed.changePasswordHash(encoder.encode(java.util.UUID.randomUUID().toString()));
        when(accounts.findById(1L)).thenReturn(Optional.of(changed));

        mvc.perform(get("/api/v1/auth/me").session(authenticated))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertTrue(authenticated.isInvalid());
    }

    @Test
    void expiredSessionCannotReadIdentity() throws Exception {
        UserAccount account = account(AccountRole.PATIENT, true);
        stubAccount(account);
        MockHttpSession authenticated = login();
        String expiredId = authenticated.getId();
        authenticated.invalidate();

        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("JSESSIONID", expiredId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void csrfIsRequiredAndRefreshedAfterLoginAndLogout() throws Exception {
        UserAccount account = account(AccountRole.ADMIN, true);
        stubAccount(account);
        mvc.perform(post("/api/v1/public/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"test-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        MvcResult beforeLogin = csrf(null);
        MockHttpSession authenticated = login(beforeLogin);
        mvc.perform(post("/api/v1/auth/logout").session(authenticated)
                        .header("X-CSRF-TOKEN", token(beforeLogin)))
                .andExpect(status().isForbidden());

        MvcResult afterLogin = csrf(authenticated);
        mvc.perform(post("/api/v1/auth/logout").session(authenticated)
                        .header("X-CSRF-TOKEN", token(afterLogin)))
                .andExpect(status().isNoContent());
        assertTrue(authenticated.isInvalid());
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allRolesCanReadOwnIdentityButOtherRoutesStayClosed() throws Exception {
        for (AccountRole role : AccountRole.values()) {
            UserAccount account = account(role, true);
            stubAccount(account);
            MockHttpSession authenticated = login();
            mvc.perform(get("/api/v1/auth/me").session(authenticated))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(role.name()));
            mvc.perform(get("/api/v1/private/probe").session(authenticated))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/public/ping"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    private void stubAccount(UserAccount account) {
        when(accounts.findByUsername("admin")).thenReturn(Optional.of(account));
        when(accounts.findById(anyLong())).thenReturn(Optional.of(account));
    }

    private MockHttpSession login() throws Exception {
        return login(csrf(null));
    }

    private MockHttpSession login(MvcResult csrf) throws Exception {
        MockHttpSession session = session(csrf);
        MvcResult result = mvc.perform(post("/api/v1/public/auth/login").session(session)
                        .with(request -> { request.setRequestedSessionId(session.getId()); return request; })
                        .header("X-CSRF-TOKEN", token(csrf)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        return session(result);
    }

    private MvcResult csrf(MockHttpSession session) throws Exception {
        if (session == null) {
            return mvc.perform(get("/api/v1/public/csrf")).andExpect(status().isOk()).andReturn();
        }
        return mvc.perform(get("/api/v1/public/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
    }

    private static String token(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private static MockHttpSession session(MvcResult result) {
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UserAccount account(AccountRole role, boolean active) {
        UserAccount account = new UserAccount("admin", encoder.encode("test-password"), "System Admin",
                "0000000000", role);
        ReflectionTestUtils.setField(account, "id", 1L);
        ReflectionTestUtils.setField(account, "active", active);
        return account;
    }
}
