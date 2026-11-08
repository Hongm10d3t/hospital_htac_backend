package com.htac_backend.modules.administration.repository;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class AccountDatabaseIntegrationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthenticationManager authenticationManager;

    @Test
    @Transactional
    void mysqlAccountCanAuthenticateWithStoredHash() {
        String username = "m01_" + UUID.randomUUID().toString().substring(0, 12);
        String password = UUID.randomUUID().toString() + UUID.randomUUID();
        UserAccount account = accounts.saveAndFlush(new UserAccount(username, encoder.encode(password),
                "Test Account", "0000000000", AccountRole.PATIENT));

        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        assertEquals(account.getId(), ((AccountPrincipal) authentication.getPrincipal()).userId());
        assertEquals(AccountRole.PATIENT, ((AccountPrincipal) authentication.getPrincipal()).role());
        assertNotNull(account.getCreatedAt());
    }
}
