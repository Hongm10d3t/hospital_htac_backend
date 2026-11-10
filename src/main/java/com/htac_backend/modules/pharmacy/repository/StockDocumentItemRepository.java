package com.htac_backend.modules.pharmacy.repository;

import com.htac_backend.modules.pharmacy.entity.StockDocumentItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockDocumentItemRepository extends JpaRepository<StockDocumentItem, Long> {
    @Query("select distinct i.batch.id from StockDocumentItem i where i.document.id = :documentId "
            + "order by i.batch.id")
    List<Long> findBatchIdsByDocumentId(@Param("documentId") Long documentId);

    @Query("select i from StockDocumentItem i join fetch i.batch b join fetch b.medicine "
            + "where i.document.id = :documentId order by i.id")
    List<StockDocumentItem> findDetailsByDocumentId(@Param("documentId") Long documentId);
}
