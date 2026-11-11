package com.htac_backend.modules.procurement.repository;

import com.htac_backend.modules.procurement.entity.PurchaseRequestItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRequestItemRepository extends JpaRepository<PurchaseRequestItem, Long> {
    List<PurchaseRequestItem> findByRequestIdOrderById(Long requestId);
    void deleteByRequestId(Long requestId);
}
