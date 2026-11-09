package com.htac_backend.modules.scheduling.repository;

import com.htac_backend.modules.scheduling.entity.Appointment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    boolean existsBySlotId(Long slotId);

    @Query("select appointment.id from Appointment appointment where appointment.slotId = :slotId "
            + "and appointment.status in (com.htac_backend.modules.scheduling.entity.AppointmentStatus.PENDING, "
            + "com.htac_backend.modules.scheduling.entity.AppointmentStatus.CONFIRMED)")
    List<Long> findHoldingIds(@Param("slotId") Long slotId);
}
