package com.htac_backend.modules.administration.security;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.security.AuthenticatedActor;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.CredentialsContainer;

public final class AccountPrincipal implements UserDetails, AuthenticatedActor, CredentialsContainer, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final AccountRole role;
    private final boolean active;
    private final long credentialVersion;
    private String passwordHash;

    public AccountPrincipal(UserAccount account) {
        this.userId = account.getId();
        this.username = account.getUsername();
        this.passwordHash = account.getPasswordHash();
        this.role = account.getRole();
        this.active = account.isActive();
        this.credentialVersion = account.getCredentialVersion();
    }

    @Override
    public Long userId() { return userId; }

    public AccountRole role() { return role; }
    public long credentialVersion() { return credentialVersion; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isEnabled() { return active; }

    @Override
    public void eraseCredentials() { passwordHash = null; }
}
