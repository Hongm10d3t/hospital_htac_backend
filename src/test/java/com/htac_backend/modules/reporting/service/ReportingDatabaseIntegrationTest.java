package com.htac_backend.modules.reporting.service;

import com.htac_backend.modules.administration.entity.AccountRole;
import com.htac_backend.modules.administration.entity.UserAccount;
import com.htac_backend.modules.administration.repository.UserAccountRepository;
import com.htac_backend.modules.administration.security.AccountPrincipal;
import com.htac_backend.modules.pharmacy.service.InventoryQueryService;
import com.htac_backend.modules.procurement.service.PurchaseRequestService;
import com.htac_backend.shared.api.InvalidRequestException;
import com.htac_backend.shared.api.PageQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "HIS_TEST_DB_URL", matches = ".+")
class ReportingDatabaseIntegrationTest {
    private static final LocalDate FROM = LocalDate.of(2026, 11, 11);
    private static final LocalDate TO = FROM.plusDays(1);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("HIS_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("HIS_TEST_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("HIS_TEST_DB_PASSWORD"));
    }

    @Autowired UserAccountRepository accounts;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClinicalReportService clinical;
    @Autowired PrescriptionReportService prescriptions;
    @Autowired PharmacyReportService pharmacy;
    @Autowired InventoryQueryService inventory;
    @Autowired PurchaseRequestService procurement;

    @Test
    void reportsReconcileWithClinicalPharmacyAndProcurementSources() {
        String tag = UUID.randomUUID().toString().substring(0, 10);
        var admin = account("a" + tag, AccountRole.ADMIN);
        var receptionist = account("r" + tag, AccountRole.RECEPTIONIST);
        var doctor = account("d" + tag, AccountRole.DOCTOR);
        var otherDoctor = account("e" + tag, AccountRole.DOCTOR);
        var pharmacist = account("p" + tag, AccountRole.PHARMACIST);
        try {
            long specialty = specialty("S" + tag);
            long room = room("R" + tag, specialty);
            jdbc.update("INSERT INTO doctor_profile (user_id,specialty_id) VALUES (?,?)", doctor.getId(), specialty);
            jdbc.update("INSERT INTO doctor_profile (user_id,specialty_id) VALUES (?,?)", otherDoctor.getId(), specialty);
            long firstSlot = slot(room, doctor.getId(), "2026-11-10 17:30:00");
            long nextSlot = slot(room, otherDoctor.getId(), "2026-11-11 17:00:00");
            long patient = patient("P" + tag);
            appointment("A" + tag, patient, firstSlot, "PENDING");
            long attendedAppointment = appointment("B" + tag, patient, firstSlot, "COMPLETED");
            long nextAppointment = appointment("C" + tag, patient, nextSlot, "COMPLETED");
            long visit = visit("V" + tag, patient, attendedAppointment, firstSlot, room,
                    "2026-11-11", 1, "2026-11-10 17:35:00", "COMPLETED");
            visit("W" + tag, patient, null, firstSlot, room,
                    "2026-11-11", 2, "2026-11-10 17:40:00", "NOT_COMPLETED");
            visit("X" + tag, patient, null, firstSlot, room,
                    "2026-11-11", 3, "2026-11-10 17:45:00", "VOIDED");
            visit("Y" + tag, patient, nextAppointment, nextSlot, room,
                    "2026-11-12", 1, "2026-11-11 17:00:00", "COMPLETED");

            long medicine = medicine("M" + tag);
            long batchA = batch("B" + tag, medicine);
            long batchB = batch("C" + tag, medicine);
            long supplier = supplier("T" + tag);
            long issued = prescription("RX" + tag, visit, doctor.getId(), "ISSUED", true);
            long supplement = prescription("RY" + tag, visit, doctor.getId(), "ISSUED", false);
            long draft = prescription("RZ" + tag, visit, doctor.getId(), "DRAFT", false);
            long item = prescriptionItem(issued, medicine, "10", "4", "STOCKOUT");
            prescriptionItem(supplement, medicine, "5", "0", null);
            prescriptionItem(draft, medicine, "99", "0", null);

            long approvedRequest = purchase("PR" + tag, supplier, "APPROVED", admin.getId());
            purchaseItem(approvedRequest, medicine, "100");
            long pendingRequest = purchase("PS" + tag, supplier, "PENDING", null);
            purchaseItem(pendingRequest, medicine, "30");

            stockItem(stock("O1" + tag, "OPENING", "POSTED", "MAIN", "AVAILABLE",
                    null, null, null, null, null, pharmacist.getId()), batchA, "20", null, null);
            stockItem(stock("O2" + tag, "OPENING", "POSTED", "MAIN", "AVAILABLE",
                    null, null, null, null, null, pharmacist.getId()), batchB, "10", null, null);
            long transfer = stock("T1" + tag, "TRANSFER", "POSTED", "MAIN", "AVAILABLE",
                    "COUNTER", "AVAILABLE", null, null, null, pharmacist.getId());
            stockItem(transfer, batchA, "8", null, null);
            stockItem(transfer, batchB, "4", null, null);
            long dispA = stock("D1" + tag, "DISPENSE", "POSTED", "COUNTER", "AVAILABLE",
                    null, null, null, issued, null, pharmacist.getId());
            long sourceItem = stockItem(dispA, batchA, "3", item, null);
            stockItem(stock("D2" + tag, "DISPENSE", "POSTED", "COUNTER", "AVAILABLE",
                    null, null, null, issued, null, pharmacist.getId()), batchB, "2", item, null);
            long nextDayDispense = stock("D4" + tag, "DISPENSE", "POSTED", "COUNTER", "AVAILABLE",
                    null, null, null, issued, null, pharmacist.getId());
            jdbc.update("UPDATE stock_document SET occurred_at=? WHERE id=?",
                    "2026-11-11 17:00:00", nextDayDispense);
            stockItem(nextDayDispense, batchA, "1", item, null);
            stockItem(stock("D3" + tag, "DISPENSE", "DRAFT", "COUNTER", "AVAILABLE",
                    null, null, null, issued, null, pharmacist.getId()), batchA, "2", item, null);
            stockItem(stock("P1" + tag, "PATIENT_RETURN", "POSTED", "COUNTER", "QUARANTINE",
                    null, null, null, issued, null, pharmacist.getId()), batchA, "1", item, sourceItem);
            stockItem(stock("R1" + tag, "RECEIPT", "POSTED", "MAIN", "AVAILABLE",
                    null, null, supplier, null, approvedRequest, pharmacist.getId()), batchA, "40", null, null);
            stockItem(stock("R2" + tag, "RECEIPT", "POSTED", "MAIN", "AVAILABLE",
                    null, null, supplier, null, approvedRequest, pharmacist.getId()), batchB, "60", null, null);
            stockItem(stock("R3" + tag, "RECEIPT", "DRAFT", "MAIN", "AVAILABLE",
                    null, null, supplier, null, approvedRequest, pharmacist.getId()), batchA, "7", null, null);
            stockItem(stock("R4" + tag, "RECEIPT", "CANCELLED", "MAIN", "AVAILABLE",
                    null, null, supplier, null, approvedRequest, pharmacist.getId()), batchA, "9", null, null);

            authenticate(receptionist);
            var appointments = clinical.appointments(FROM, TO, null, null, null, page("date"));
            assertEquals(2, appointments.items().stream().mapToLong(row -> row.count()).sum());
            assertEquals(FROM, appointments.items().getFirst().date());
            var visits = clinical.visits(FROM, TO, null, null, specialty, null, page("date"));
            assertEquals(2, visits.items().stream().mapToLong(row -> row.count()).sum());
            assertTrue(visits.items().stream().anyMatch(row -> row.status().equals("NOT_COMPLETED")));
            assertFalse(visits.items().stream().anyMatch(row -> row.status().equals("VOIDED")));
            assertEquals(1, clinical.visits(TO, TO.plusDays(1), null, null, null,
                    null, page("date")).items().stream().mapToLong(row -> row.count()).sum());
            assertThrows(InvalidRequestException.class, () -> clinical.visits(TO, FROM,
                    null, null, null, null, page("date")));

            authenticate(doctor);
            assertEquals(2, clinical.appointments(FROM, TO.plusDays(1), null,
                    null, null, page("date")).items().stream().mapToLong(row -> row.count()).sum());
            assertThrows(AccessDeniedException.class, () -> clinical.visits(FROM, TO,
                    otherDoctor.getId(), null, null, null, page("date")));
            authenticate(otherDoctor);
            assertEquals(0, clinical.visits(FROM, TO, null, null, null,
                    null, page("date")).totalItems());
            assertThrows(AccessDeniedException.class, () -> pharmacy.balances(null, null,
                    null, null, page("medicineId")));

            authenticate(admin);
            var medicineReport = prescriptions.medicines(FROM, TO, medicine, page("medicineId"));
            assertEquals(1, medicineReport.totalItems());
            assertEquals(medicine, medicineReport.filters().get("medicineId"));
            var row = medicineReport.items().getFirst();
            assertAmount("15", row.prescribedQuantity());
            assertAmount("5", row.dispensedQuantity());
            assertAmount("4", row.currentUnfilledQuantity());
            assertEquals(1, row.uncheckedLineCount());
            var nextDayMedicine = prescriptions.medicines(TO, TO.plusDays(1), medicine,
                    page("medicineId")).items().getFirst();
            assertAmount("0", nextDayMedicine.prescribedQuantity());
            assertAmount("1", nextDayMedicine.dispensedQuantity());
            var detail = prescriptions.prescription(issued);
            assertTrue(detail.reviewed());
            assertAmount("6", detail.items().getFirst().dispensedQuantity());
            assertEquals(3, prescriptions.dispenseLines(issued, page("itemId")).totalItems());
            assertFalse(prescriptions.prescription(supplement).reviewed());
            assertThrows(com.htac_backend.shared.api.NotFoundException.class,
                    () -> prescriptions.prescription(draft));
            jdbc.update("UPDATE prescription_item SET unfilled_quantity=? WHERE id=?",
                    new BigDecimal("2"), item);
            assertAmount("2", prescriptions.medicines(FROM, TO, medicine,
                    page("medicineId")).items().getFirst().currentUnfilledQuantity());
            assertEquals(0, pharmacy.movements(FROM, TO, medicine, null, null, null,
                    "TRANSFER", page("medicineId")).items().stream()
                    .map(r -> r.netQuantity()).reduce(BigDecimal.ZERO, BigDecimal::add).signum());
            var transferLines = pharmacy.movementLines(FROM, TO, medicine, batchA,
                    null, null, "TRANSFER", page("itemId"));
            assertEquals(2, transferLines.totalItems());
            assertEquals(0, transferLines.items().stream().map(r -> r.deltaQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add).signum());
            var approvedRow = pharmacy.purchaseRequests("APPROVED", supplier,
                    null, null, page("requestId")).items().getFirst();
            assertAmount("100", approvedRow.receivedQuantity());
            assertAmount("0", approvedRow.remainingQuantity());
            assertAmount("0", pharmacy.purchaseRequests("PENDING", supplier,
                    null, null, page("requestId")).items().getFirst().receivedQuantity());
            assertAmount("100", procurement.get(approvedRequest).items().getFirst().receivedQuantity());

            authenticate(pharmacist);
            var sourceInventory = inventory.medicine(medicine);
            var reportBalances = pharmacy.balances(medicine, null, null, null, page("medicineId"));
            assertAmount("125", reportBalances.items().stream().map(r -> r.physicalQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            assertEquals(0, sourceInventory.physicalQuantity().compareTo(reportBalances.items()
                    .stream().map(r -> r.physicalQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add)));
            assertEquals(0, sourceInventory.dispensableQuantity().compareTo(reportBalances.items()
                    .stream().map(r -> r.dispensableQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add)));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UserAccount account(String username, AccountRole role) {
        return accounts.saveAndFlush(new UserAccount(username, "invalid-test-hash",
                "Synthetic staff", "0000000000", role));
    }

    private long specialty(String code) {
        jdbc.update("INSERT INTO specialty (code,name) VALUES (?,?)", code, "Synthetic");
        return id("specialty", code);
    }

    private long room(String code, long specialty) {
        jdbc.update("INSERT INTO clinic_room (code,name,specialty_id) VALUES (?,?,?)",
                code, "Synthetic", specialty);
        return id("clinic_room", code);
    }

    private long slot(long room, long doctor, String start) {
        jdbc.update("INSERT INTO clinic_slot (room_id,doctor_id,start_at,end_at,capacity,status) "
                + "VALUES (?,?,?,DATE_ADD(?, INTERVAL 30 MINUTE),10,'OPEN')", room, doctor, start, start);
        return jdbc.queryForObject("SELECT MAX(id) FROM clinic_slot WHERE room_id=? AND doctor_id=?",
                Long.class, room, doctor);
    }

    private long patient(String code) {
        jdbc.update("INSERT INTO patient (patient_code,full_name,date_of_birth,gender) "
                + "VALUES (?,?,?,?)", code, "Synthetic", "2000-01-01", "OTHER");
        return id("patient", code);
    }

    private long appointment(String code, long patient, long slot, String status) {
        jdbc.update("INSERT INTO appointment (appointment_code,patient_id,slot_id,reason,status) "
                + "VALUES (?,?,?,?,?)", code, patient, slot, "Synthetic", status);
        return id("appointment", code);
    }

    private long visit(String code, long patient, Long appointment, long slot, long room,
            String queueDate, int queueNumber, String arrivedAt, String status) {
        jdbc.update("INSERT INTO medical_visit (visit_code,patient_id,appointment_id,slot_id,room_id,"
                + "queue_date,queue_number,arrived_at,status,diagnosis,outcome_reason) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?)", code, patient, appointment, slot, room,
                queueDate, queueNumber, arrivedAt, status,
                status.equals("COMPLETED") ? "Synthetic" : null,
                Set.of("NOT_COMPLETED", "VOIDED").contains(status) ? "Synthetic" : null);
        return id("medical_visit", code);
    }

    private long medicine(String code) {
        jdbc.update("INSERT INTO medicine (code,name,active_ingredient,strength,dosage_form,base_unit,"
                + "minimum_stock,lead_time_days,is_active) VALUES (?,?,?,?,?,?,?,?,?)",
                code, "Synthetic", "Synthetic", "1 mg", "tablet", "tablet", 0, 0, true);
        return id("medicine", code);
    }

    private long batch(String code, long medicine) {
        jdbc.update("INSERT INTO medicine_batch (medicine_id,batch_number,expiry_date,status) "
                + "VALUES (?,?,?,?)", medicine, code, "2030-01-01", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM medicine_batch WHERE medicine_id=? AND batch_number=?",
                Long.class, medicine, code);
    }

    private long supplier(String code) {
        jdbc.update("INSERT INTO supplier (code,name,is_active) VALUES (?,?,?)", code, "Synthetic", true);
        return id("supplier", code);
    }

    private long prescription(String code, long visit, long doctor, String status, boolean checked) {
        jdbc.update("INSERT INTO prescription (prescription_code,visit_id,doctor_id,status,issued_at,"
                + "dispensing_checked_at) VALUES (?,?,?,?,?,?)", code, visit, doctor, status,
                status.equals("ISSUED") ? "2026-11-10 18:00:00" : null,
                checked ? "2026-11-10 20:00:00" : null);
        return id("prescription", code);
    }

    private long prescriptionItem(long prescription, long medicine, String quantity,
            String unfilled, String reason) {
        jdbc.update("INSERT INTO prescription_item (prescription_id,medicine_id,quantity,"
                + "usage_instruction,unfilled_quantity,unfilled_reason) VALUES (?,?,?,?,?,?)",
                prescription, medicine, new BigDecimal(quantity), "Synthetic",
                new BigDecimal(unfilled), reason);
        return jdbc.queryForObject("SELECT id FROM prescription_item WHERE prescription_id=? AND medicine_id=?",
                Long.class, prescription, medicine);
    }

    private long purchase(String code, long supplier, String status, Long reviewer) {
        jdbc.update("INSERT INTO purchase_request (request_code,supplier_id,status,reviewed_by,reviewed_at,"
                + "ordered_at,expected_delivery_date,note) VALUES (?,?,?,?,?,?,?,?)", code, supplier,
                status, reviewer, reviewer == null ? null : "2026-11-10 18:00:00",
                reviewer == null ? null : "2026-11-10 18:10:00", "2026-11-12", "Synthetic");
        return id("purchase_request", code);
    }

    private void purchaseItem(long request, long medicine, String quantity) {
        jdbc.update("INSERT INTO purchase_request_item (request_id,medicine_id,quantity) VALUES (?,?,?)",
                request, medicine, new BigDecimal(quantity));
    }

    private long stock(String code, String type, String status, String location, String bucket,
            String destinationLocation, String destinationBucket, Long supplier, Long prescription,
            Long request, Long poster) {
        jdbc.update("INSERT INTO stock_document (document_code,type,occurred_at,status,stock_location,"
                + "stock_bucket,destination_location,destination_bucket,supplier_id,prescription_id,"
                + "purchase_request_id,posted_at,posted_by,note) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                code, type, "2026-11-10 19:00:00", status, location, bucket, destinationLocation,
                destinationBucket, supplier, prescription, request,
                status.equals("POSTED") ? "2026-11-10 19:00:00" : null,
                status.equals("POSTED") ? poster : null,
                Set.of("PATIENT_RETURN", "SUPPLIER_RETURN", "DISPOSAL", "ADJUSTMENT")
                        .contains(type) ? "Synthetic" : null);
        return id("stock_document", code);
    }

    private long stockItem(long document, long batch, String quantity,
            Long prescriptionItem, Long sourceItem) {
        jdbc.update("INSERT INTO stock_document_item (document_id,batch_id,quantity,"
                + "prescription_item_id,source_item_id) VALUES (?,?,?,?,?)", document, batch,
                new BigDecimal(quantity), prescriptionItem, sourceItem);
        return jdbc.queryForObject("SELECT MAX(id) FROM stock_document_item WHERE document_id=?",
                Long.class, document);
    }

    private long id(String table, String code) {
        String column = switch (table) {
            case "patient" -> "patient_code";
            case "appointment" -> "appointment_code";
            case "medical_visit" -> "visit_code";
            case "prescription" -> "prescription_code";
            case "purchase_request" -> "request_code";
            case "stock_document" -> "document_code";
            default -> "code";
        };
        return jdbc.queryForObject("SELECT id FROM " + table + " WHERE " + column + "=?",
                Long.class, code);
    }

    private PageQuery page(String sort) {
        return PageQuery.parse(0, 100, sort + ",ASC", sort, Set.of(sort));
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private void authenticate(UserAccount account) {
        AccountPrincipal principal = new AccountPrincipal(account);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
