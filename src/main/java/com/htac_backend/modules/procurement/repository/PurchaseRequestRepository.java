package com.htac_backend.modules.procurement.repository;

import com.htac_backend.modules.procurement.entity.PurchaseRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PurchaseRequest r where r.id = :id")
    Optional<PurchaseRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("select r from PurchaseRequest r where (:status is null or r.status = :status) "
            + "and (:supplierId is null or r.supplierId = :supplierId)")
    Page<PurchaseRequest> search(@Param("status") String status,
            @Param("supplierId") Long supplierId, Pageable pageable);
}
