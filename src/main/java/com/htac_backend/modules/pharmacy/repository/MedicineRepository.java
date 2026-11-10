package com.htac_backend.modules.pharmacy.repository;

import com.htac_backend.modules.pharmacy.entity.Medicine;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select medicine from Medicine medicine where medicine.id = :id")
    Optional<Medicine> findByIdForUpdate(@Param("id") Long id);

    @Query("select medicine from Medicine medicine where (:active is null or medicine.active = :active) "
            + "and (:term is null or lower(medicine.code) like lower(concat('%', :term, '%')) "
            + "or lower(medicine.name) like lower(concat('%', :term, '%')))")
    Page<Medicine> search(@Param("term") String term, @Param("active") Boolean active,
            Pageable pageable);
}
