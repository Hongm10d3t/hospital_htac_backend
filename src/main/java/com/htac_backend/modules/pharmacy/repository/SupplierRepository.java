package com.htac_backend.modules.pharmacy.repository;

import com.htac_backend.modules.pharmacy.entity.Supplier;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select supplier from Supplier supplier where supplier.id = :id")
    Optional<Supplier> findByIdForUpdate(@Param("id") Long id);

    @Query("select supplier from Supplier supplier where (:active is null or supplier.active = :active) "
            + "and (:term is null or lower(supplier.code) like lower(concat('%', :term, '%')) "
            + "or lower(supplier.name) like lower(concat('%', :term, '%')))")
    Page<Supplier> search(@Param("term") String term, @Param("active") Boolean active,
            Pageable pageable);
}
