package com.htac_backend.modules.pharmacy.repository;

import com.htac_backend.modules.pharmacy.entity.MedicineBatch;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicineBatchRepository extends JpaRepository<MedicineBatch, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from MedicineBatch b join fetch b.medicine where b.id = :id")
    Optional<MedicineBatch> findByIdForUpdate(@Param("id") Long id);

    @Query("select b from MedicineBatch b join fetch b.medicine where b.id = :id")
    Optional<MedicineBatch> findDetail(@Param("id") Long id);

    boolean existsByMedicineIdAndBatchNumber(Long medicineId, String batchNumber);
    boolean existsByMedicineIdAndBatchNumberAndIdNot(Long medicineId, String batchNumber, Long id);

    @Query(value = "select b from MedicineBatch b where (:medicineId is null or b.medicine.id = :medicineId) "
            + "and (:status is null or b.status = :status) "
            + "and (:term is null or lower(b.batchNumber) like lower(concat('%', :term, '%')))",
            countQuery = "select count(b) from MedicineBatch b where "
                    + "(:medicineId is null or b.medicine.id = :medicineId) "
                    + "and (:status is null or b.status = :status) "
                    + "and (:term is null or lower(b.batchNumber) like lower(concat('%', :term, '%')))")
    Page<MedicineBatch> search(@Param("medicineId") Long medicineId,
            @Param("status") String status, @Param("term") String term, Pageable pageable);
}
