package com.htac_backend.modules.administration.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapAdminServiceTest {
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final BootstrapAdminService bootstrap = new BootstrapAdminService(accounts, encoder);

    @Test
    void createsOnlyFirstAdminWithEncodedPassword() {
        when(encoder.encode("a-long-secret")).thenReturn("{bcrypt}encoded");
        assertTrue(bootstrap.createIfMissing("admin", "a-long-secret", "Administrator", "0000000000"));
        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(accounts).saveAndFlush(saved.capture());
        assertEquals(AccountRole.ADMIN, saved.getValue().getRole());
        assertEquals("{bcrypt}encoded", saved.getValue().getPasswordHash());
        assertFalse("a-long-secret".equals(saved.getValue().getPasswordHash()));
    }

    @Test
    void existingAdminIsNeverOverwritten() {
        when(accounts.existsByRole(AccountRole.ADMIN)).thenReturn(true);
        assertFalse(bootstrap.createIfMissing("another", "a-long-secret", "Administrator", "0000000000"));
        verify(accounts, never()).saveAndFlush(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    void weakBootstrapPasswordIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> bootstrap.createIfMissing("admin", "short", "Administrator", "0000000000"));
        verify(accounts, never()).saveAndFlush(any());
    }
}
