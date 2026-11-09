package com.htac_backend.modules.administration.repository;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);
    boolean existsByRole(AccountRole role);
    boolean existsByUsername(String username);
    Page<UserAccount> findByRoleIn(Set<AccountRole> roles, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select account from UserAccount account where account.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") Long id);
}
