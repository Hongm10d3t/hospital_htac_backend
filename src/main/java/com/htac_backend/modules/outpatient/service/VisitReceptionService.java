package com.htac_backend.modules.outpatient.service;

import com.htac_backend.modules.outpatient.dto.ReceptionVisit;
import com.htac_backend.modules.outpatient.entity.MedicalVisit;
import com.htac_backend.modules.outpatient.entity.VisitStatus;
import com.htac_backend.modules.outpatient.repository.MedicalVisitRepository;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** M05 owns medical_visit; M04 coordinates the slot and room locks before writing. */
@Service
public class VisitReceptionService {
    public static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final MedicalVisitRepository visits;

    public VisitReceptionService(MedicalVisitRepository visits) {
        this.visits = visits;
    }

    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public Optional<ReceptionVisit> byAppointment(Long appointmentId) {
        return visits.findByAppointmentId(appointmentId).map(ReceptionVisit::from);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ReceptionVisit create(Long patientId, Long appointmentId, Long slotId, Long roomId,
            Instant arrivedAt, String reason) {
        LocalDate queueDate = arrivedAt.atZone(HOSPITAL_ZONE).toLocalDate();
        int queueNumber = visits.maxQueueNumber(roomId, queueDate) + 1;
        String code = "V" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        MedicalVisit created = visits.saveAndFlush(new MedicalVisit(code, patientId, appointmentId,
                slotId, roomId, queueDate, queueNumber, arrivedAt, reason));
        return ReceptionVisit.from(created);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceptionVisit> waiting(LocalDate date, Long roomId, Long slotId, PageQuery page) {
        return PageResponse.from(visits.waiting(VisitStatus.WAITING, date, roomId, slotId,
                page.toPageRequest()).map(ReceptionVisit::from));
    }
}
