package com.htac_backend.modules.reporting.controller;

import com.htac_backend.modules.reporting.dto.AppointmentReportRow;
import com.htac_backend.modules.reporting.dto.DispenseReportLine;
import com.htac_backend.modules.reporting.dto.PrescriptionMedicineReportRow;
import com.htac_backend.modules.reporting.dto.PrescriptionReportDetail;
import com.htac_backend.modules.reporting.dto.PurchaseRequestReportRow;
import com.htac_backend.modules.reporting.dto.ReportPage;
import com.htac_backend.modules.reporting.dto.StockBalanceReportRow;
import com.htac_backend.modules.reporting.dto.StockMovementReportLine;
import com.htac_backend.modules.reporting.dto.StockMovementReportRow;
import com.htac_backend.modules.reporting.dto.VisitReportRow;
import com.htac_backend.modules.reporting.service.ClinicalReportService;
import com.htac_backend.modules.reporting.service.PharmacyReportService;
import com.htac_backend.modules.reporting.service.PrescriptionReportService;
import com.htac_backend.shared.api.PageQuery;
import com.htac_backend.shared.api.PageResponse;
import com.htac_backend.shared.api.FilterQuery;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportingController {
    private final ClinicalReportService clinical;
    private final PrescriptionReportService prescriptions;
    private final PharmacyReportService pharmacy;

    public ReportingController(ClinicalReportService clinical, PrescriptionReportService prescriptions,
            PharmacyReportService pharmacy) {
        this.clinical = clinical;
        this.prescriptions = prescriptions;
        this.pharmacy = pharmacy;
    }

    @GetMapping("/appointments")
    public ReportPage<AppointmentReportRow> appointments(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long roomId, @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam Map<String, String> params) {
        allowed(params, "from", "to", "doctorId", "roomId", "status", "page", "size", "sort");
        return clinical.appointments(from, to, doctorId, roomId, status,
                query(page, size, sort, "date,ASC", "date", Set.of("date", "count")));
    }

    @GetMapping("/visits")
    public ReportPage<VisitReportRow> visits(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long roomId, @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) String status, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam Map<String, String> params) {
        allowed(params, "from", "to", "doctorId", "roomId", "specialtyId", "status",
                "page", "size", "sort");
        return clinical.visits(from, to, doctorId, roomId, specialtyId, status,
                query(page, size, sort, "date,ASC", "date", Set.of("date", "count")));
    }

    @GetMapping("/prescription-medicines")
    public ReportPage<PrescriptionMedicineReportRow> prescriptionMedicines(
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long medicineId, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam Map<String, String> params) {
        allowed(params, "from", "to", "medicineId", "page", "size", "sort");
        return prescriptions.medicines(from, to, medicineId,
                query(page, size, sort, "medicineId,ASC", "medicineId",
                        Set.of("medicineId", "prescribedQuantity", "dispensedQuantity")));
    }

    @GetMapping("/prescriptions/{id}")
    public PrescriptionReportDetail prescription(@PathVariable Long id,
            @RequestParam Map<String, String> params) {
        allowed(params);
        return prescriptions.prescription(id);
    }

    @GetMapping("/prescriptions/{id}/dispense-lines")
    public PageResponse<DispenseReportLine> dispenseLines(@PathVariable Long id,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam Map<String, String> params) {
        allowed(params, "page", "size", "sort");
        return prescriptions.dispenseLines(id,
                query(page, size, sort, "occurredAt,ASC", "occurredAt",
                        Set.of("occurredAt", "documentId", "itemId")));
    }

    @GetMapping("/stock-balances")
    public PageResponse<StockBalanceReportRow> stockBalances(
            @RequestParam(required = false) Long medicineId, @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String location, @RequestParam(required = false) String bucket,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam Map<String, String> params) {
        allowed(params, "medicineId", "batchId", "location", "bucket", "page", "size", "sort");
        return pharmacy.balances(medicineId, batchId, location, bucket,
                query(page, size, sort, "medicineId,ASC", "medicineId",
                        Set.of("medicineId", "batchId", "physicalQuantity")));
    }

    @GetMapping("/stock-movements")
    public ReportPage<StockMovementReportRow> stockMovements(
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long medicineId, @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String location, @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String type, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam Map<String, String> params) {
        allowed(params, "from", "to", "medicineId", "batchId", "location", "bucket",
                "type", "page", "size", "sort");
        return pharmacy.movements(from, to, medicineId, batchId, location, bucket, type,
                query(page, size, sort, "medicineId,ASC", "medicineId",
                        Set.of("medicineId", "batchId", "type", "netQuantity")));
    }

    @GetMapping("/stock-movement-lines")
    public ReportPage<StockMovementReportLine> stockMovementLines(
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long medicineId, @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String location, @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String type, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
            @RequestParam Map<String, String> params) {
        allowed(params, "from", "to", "medicineId", "batchId", "location", "bucket",
                "type", "page", "size", "sort");
        return pharmacy.movementLines(from, to, medicineId, batchId, location, bucket, type,
                query(page, size, sort, "occurredAt,ASC", "occurredAt",
                        Set.of("occurredAt", "documentId", "itemId")));
    }

    @GetMapping("/purchase-requests")
    public PageResponse<PurchaseRequestReportRow> purchaseRequests(
            @RequestParam(required = false) String status, @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) LocalDate expectedFrom,
            @RequestParam(required = false) LocalDate expectedTo,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort, @RequestParam Map<String, String> params) {
        allowed(params, "status", "supplierId", "expectedFrom", "expectedTo",
                "page", "size", "sort");
        return pharmacy.purchaseRequests(status, supplierId, expectedFrom, expectedTo,
                query(page, size, sort, "requestId,DESC", "requestId",
                        Set.of("requestId", "expectedDeliveryDate", "status")));
    }

    private PageQuery query(Integer page, Integer size, String sort, String defaultSort,
            String defaultField, Set<String> allowed) {
        return PageQuery.parse(page, size, sort == null ? defaultSort : sort, defaultField, allowed);
    }

    private void allowed(Map<String, String> params, String... names) {
        FilterQuery.parse(params, Set.of(names));
    }
}
