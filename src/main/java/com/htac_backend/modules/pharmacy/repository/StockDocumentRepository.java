package com.htac_backend.modules.pharmacy.repository;

import com.htac_backend.modules.pharmacy.entity.StockDocument;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockDocumentRepository extends JpaRepository<StockDocument, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from StockDocument d where d.id = :id")
    Optional<StockDocument> findByIdForUpdate(@Param("id") Long id);

    boolean existsByDocumentCode(String documentCode);
}
