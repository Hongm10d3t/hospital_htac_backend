-- All DATETIME(6) values represent UTC instants. Business dates use the hospital zone.
-- MySQL 8.0.16+ is required for CHECK constraints.

CREATE TABLE user_account (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    role VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uq_user_username UNIQUE (username),
    CONSTRAINT ck_user_role CHECK (role IN ('ADMIN','RECEPTIONIST','DOCTOR','PHARMACIST','PATIENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE patient (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    patient_code VARCHAR(30) NOT NULL,
    user_id BIGINT NULL,
    citizen_id VARCHAR(12) NULL,
    full_name VARCHAR(150) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(10) NOT NULL,
    address TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uq_patient_code UNIQUE (patient_code),
    CONSTRAINT uq_patient_user UNIQUE (user_id),
    CONSTRAINT uq_patient_citizen UNIQUE (citizen_id),
    CONSTRAINT fk_patient_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT ck_patient_gender CHECK (gender IN ('MALE','FEMALE','OTHER')),
    CONSTRAINT ck_patient_citizen CHECK (citizen_id IS NULL OR CHAR_LENGTH(citizen_id) = 12)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE specialty (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uq_specialty_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE clinic_room (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    specialty_id BIGINT NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(200) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uq_room_code UNIQUE (code),
    CONSTRAINT fk_room_specialty FOREIGN KEY (specialty_id) REFERENCES specialty(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE doctor_profile (
    user_id BIGINT NOT NULL PRIMARY KEY,
    specialty_id BIGINT NOT NULL,
    avatar_url VARCHAR(500) NULL,
    professional_title VARCHAR(100) NULL,
    position VARCHAR(150) NULL,
    practice_start_year SMALLINT NULL,
    introduction TEXT NULL,
    clinical_expertise TEXT NULL,
    certifications TEXT NULL,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT fk_doctor_user FOREIGN KEY (user_id) REFERENCES user_account(id),
    CONSTRAINT fk_doctor_specialty FOREIGN KEY (specialty_id) REFERENCES specialty(id),
    CONSTRAINT ck_doctor_practice_year CHECK (practice_start_year IS NULL OR practice_start_year BETWEEN 1900 AND 9999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE clinic_slot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    capacity INT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT fk_slot_room FOREIGN KEY (room_id) REFERENCES clinic_room(id),
    CONSTRAINT fk_slot_doctor FOREIGN KEY (doctor_id) REFERENCES doctor_profile(user_id),
    CONSTRAINT ck_slot_time CHECK (end_at > start_at),
    CONSTRAINT ck_slot_capacity CHECK (capacity > 0),
    CONSTRAINT ck_slot_status CHECK (status IN ('OPEN','CLOSED')),
    INDEX ix_slot_room_time (room_id, start_at, end_at),
    INDEX ix_slot_doctor_time (doctor_id, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE appointment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_code VARCHAR(30) NOT NULL,
    patient_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    confirmed_at DATETIME(6) NULL,
    confirmed_by BIGINT NULL,
    closed_at DATETIME(6) NULL,
    non_completion_reason TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uq_appointment_code UNIQUE (appointment_code),
    CONSTRAINT fk_appointment_patient FOREIGN KEY (patient_id) REFERENCES patient(id),
    CONSTRAINT fk_appointment_slot FOREIGN KEY (slot_id) REFERENCES clinic_slot(id),
    CONSTRAINT fk_appointment_confirmer FOREIGN KEY (confirmed_by) REFERENCES user_account(id),
    CONSTRAINT ck_appointment_status CHECK (status IN ('PENDING','CONFIRMED','COMPLETED','NOT_COMPLETED')),
    CONSTRAINT ck_appointment_reason CHECK (status <> 'NOT_COMPLETED' OR non_completion_reason IS NOT NULL),
    INDEX ix_appointment_slot_status (slot_id, status),
    INDEX ix_appointment_patient_created (patient_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medical_visit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    visit_code VARCHAR(30) NOT NULL,
    patient_id BIGINT NOT NULL,
    appointment_id BIGINT NULL,
    slot_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    queue_date DATE NOT NULL,
    queue_number INT NOT NULL,
    arrived_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    reason TEXT NULL,
    clinical_notes TEXT NULL,
    diagnosis_code VARCHAR(20) NULL,
    diagnosis TEXT NULL,
    treatment_plan TEXT NULL,
    follow_up_date DATE NULL,
    outcome_reason TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uq_visit_code UNIQUE (visit_code),
    CONSTRAINT uq_visit_appointment UNIQUE (appointment_id),
    CONSTRAINT uq_visit_queue UNIQUE (room_id, queue_date, queue_number),
    CONSTRAINT fk_visit_patient FOREIGN KEY (patient_id) REFERENCES patient(id),
    CONSTRAINT fk_visit_appointment FOREIGN KEY (appointment_id) REFERENCES appointment(id),
    CONSTRAINT fk_visit_slot FOREIGN KEY (slot_id) REFERENCES clinic_slot(id),
    CONSTRAINT fk_visit_room FOREIGN KEY (room_id) REFERENCES clinic_room(id),
    CONSTRAINT ck_visit_queue CHECK (queue_number > 0),
    CONSTRAINT ck_visit_status CHECK (status IN ('WAITING','IN_PROGRESS','COMPLETED','NOT_COMPLETED','VOIDED')),
    CONSTRAINT ck_visit_diagnosis CHECK (status <> 'COMPLETED' OR diagnosis IS NOT NULL),
    CONSTRAINT ck_visit_outcome CHECK (status NOT IN ('NOT_COMPLETED','VOIDED') OR outcome_reason IS NOT NULL),
    INDEX ix_visit_arrived_status (arrived_at, status),
    INDEX ix_visit_patient_arrived (patient_id, arrived_at),
    INDEX ix_visit_slot_status (slot_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medicine (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    active_ingredient VARCHAR(200) NOT NULL,
    strength VARCHAR(100) NOT NULL,
    dosage_form VARCHAR(50) NOT NULL,
    base_unit VARCHAR(20) NOT NULL,
    minimum_stock DECIMAL(14,3) NOT NULL DEFAULT 0,
    lead_time_days INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uq_medicine_code UNIQUE (code),
    CONSTRAINT ck_medicine_minimum CHECK (minimum_stock >= 0),
    CONSTRAINT ck_medicine_lead CHECK (lead_time_days >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE medicine_batch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    medicine_id BIGINT NOT NULL,
    batch_number VARCHAR(60) NOT NULL,
    expiry_date DATE NOT NULL,
    status VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',
    block_reason TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uq_batch_medicine_number UNIQUE (medicine_id, batch_number),
    CONSTRAINT fk_batch_medicine FOREIGN KEY (medicine_id) REFERENCES medicine(id),
    CONSTRAINT ck_batch_status CHECK (status IN ('ACTIVE','BLOCKED')),
    CONSTRAINT ck_batch_block_reason CHECK (status <> 'BLOCKED' OR block_reason IS NOT NULL),
    INDEX ix_batch_expiry_status (expiry_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE supplier (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(20) NULL,
    address TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uq_supplier_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE prescription (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    prescription_code VARCHAR(30) NOT NULL,
    visit_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    issued_at DATETIME(6) NULL,
    status VARCHAR(15) NOT NULL DEFAULT 'DRAFT',
    note TEXT NULL,
    dispensing_checked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uq_prescription_code UNIQUE (prescription_code),
    CONSTRAINT fk_prescription_visit FOREIGN KEY (visit_id) REFERENCES medical_visit(id),
    CONSTRAINT fk_prescription_doctor FOREIGN KEY (doctor_id) REFERENCES doctor_profile(user_id),
    CONSTRAINT ck_prescription_status CHECK (status IN ('DRAFT','ISSUED','CANCELLED')),
    CONSTRAINT ck_prescription_issued CHECK (status <> 'ISSUED' OR issued_at IS NOT NULL),
    CONSTRAINT ck_prescription_cancel CHECK (status <> 'CANCELLED' OR note IS NOT NULL),
    INDEX ix_prescription_visit_status (visit_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE prescription_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    prescription_id BIGINT NOT NULL,
    medicine_id BIGINT NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    usage_instruction TEXT NOT NULL,
    unfilled_quantity DECIMAL(14,3) NOT NULL DEFAULT 0,
    unfilled_reason VARCHAR(20) NULL,
    unfilled_note TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uq_prescription_medicine UNIQUE (prescription_id, medicine_id),
    CONSTRAINT fk_item_prescription FOREIGN KEY (prescription_id) REFERENCES prescription(id),
    CONSTRAINT fk_item_medicine FOREIGN KEY (medicine_id) REFERENCES medicine(id),
    CONSTRAINT ck_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_item_unfilled CHECK (unfilled_quantity >= 0 AND unfilled_quantity <= quantity),
    CONSTRAINT ck_item_reason CHECK (unfilled_reason IS NULL OR unfilled_reason IN ('STOCKOUT','DECLINED','EXTERNAL','OTHER')),
    CONSTRAINT ck_item_other_note CHECK (unfilled_reason <> 'OTHER' OR unfilled_note IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_request (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_code VARCHAR(30) NOT NULL,
    supplier_id BIGINT NULL,
    status VARCHAR(15) NOT NULL DEFAULT 'DRAFT',
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    ordered_at DATETIME(6) NULL,
    expected_delivery_date DATE NULL,
    closed_at DATETIME(6) NULL,
    note TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uq_purchase_request_code UNIQUE (request_code),
    CONSTRAINT fk_purchase_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id),
    CONSTRAINT fk_purchase_reviewer FOREIGN KEY (reviewed_by) REFERENCES user_account(id),
    CONSTRAINT ck_purchase_status CHECK (status IN ('DRAFT','PENDING','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_purchase_review CHECK (status NOT IN ('APPROVED','REJECTED') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    INDEX ix_purchase_status_delivery (status, expected_delivery_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_request_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    medicine_id BIGINT NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    note TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uq_purchase_item_medicine UNIQUE (request_id, medicine_id),
    CONSTRAINT fk_purchase_item_request FOREIGN KEY (request_id) REFERENCES purchase_request(id),
    CONSTRAINT fk_purchase_item_medicine FOREIGN KEY (medicine_id) REFERENCES medicine(id),
    CONSTRAINT ck_purchase_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stock_document (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    document_code VARCHAR(30) NOT NULL,
    type VARCHAR(20) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    status VARCHAR(15) NOT NULL DEFAULT 'DRAFT',
    stock_location VARCHAR(10) NOT NULL,
    stock_bucket VARCHAR(15) NOT NULL,
    destination_location VARCHAR(10) NULL,
    destination_bucket VARCHAR(15) NULL,
    supplier_id BIGINT NULL,
    prescription_id BIGINT NULL,
    purchase_request_id BIGINT NULL,
    posted_at DATETIME(6) NULL,
    posted_by BIGINT NULL,
    note TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT uq_stock_document_code UNIQUE (document_code),
    CONSTRAINT fk_stock_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id),
    CONSTRAINT fk_stock_prescription FOREIGN KEY (prescription_id) REFERENCES prescription(id),
    CONSTRAINT fk_stock_request FOREIGN KEY (purchase_request_id) REFERENCES purchase_request(id),
    CONSTRAINT fk_stock_poster FOREIGN KEY (posted_by) REFERENCES user_account(id),
    CONSTRAINT ck_stock_type CHECK (type IN ('OPENING','RECEIPT','TRANSFER','DISPENSE','PATIENT_RETURN','SUPPLIER_RETURN','DISPOSAL','ADJUSTMENT')),
    CONSTRAINT ck_stock_status CHECK (status IN ('DRAFT','POSTED','CANCELLED')),
    CONSTRAINT ck_stock_location CHECK (stock_location IN ('MAIN','COUNTER')),
    CONSTRAINT ck_stock_bucket CHECK (stock_bucket IN ('AVAILABLE','QUARANTINE')),
    CONSTRAINT ck_stock_destination CHECK ((destination_location IS NULL AND destination_bucket IS NULL) OR
        (type = 'TRANSFER' AND destination_location IN ('MAIN','COUNTER') AND destination_bucket IN ('AVAILABLE','QUARANTINE'))),
    CONSTRAINT ck_stock_transfer CHECK (type <> 'TRANSFER' OR
        (destination_location IS NOT NULL AND destination_bucket IS NOT NULL AND
        (destination_location <> stock_location OR destination_bucket <> stock_bucket))),
    CONSTRAINT ck_stock_supplier_required CHECK (type NOT IN ('RECEIPT','SUPPLIER_RETURN') OR supplier_id IS NOT NULL),
    CONSTRAINT ck_stock_prescription_required CHECK (type NOT IN ('DISPENSE','PATIENT_RETURN') OR prescription_id IS NOT NULL),
    CONSTRAINT ck_stock_posted CHECK (status <> 'POSTED' OR (posted_at IS NOT NULL AND posted_by IS NOT NULL)),
    CONSTRAINT ck_stock_note CHECK (type NOT IN ('PATIENT_RETURN','SUPPLIER_RETURN','DISPOSAL','ADJUSTMENT') OR note IS NOT NULL),
    INDEX ix_stock_type_status_occurred (type, status, occurred_at),
    INDEX ix_stock_request_status (purchase_request_id, status),
    INDEX ix_stock_prescription_status (prescription_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stock_document_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    unit_cost DECIMAL(14,2) NULL,
    prescription_item_id BIGINT NULL,
    source_item_id BIGINT NULL,
    book_quantity DECIMAL(14,3) NULL,
    counted_quantity DECIMAL(14,3) NULL,
    note TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT fk_stock_item_document FOREIGN KEY (document_id) REFERENCES stock_document(id),
    CONSTRAINT fk_stock_item_batch FOREIGN KEY (batch_id) REFERENCES medicine_batch(id),
    CONSTRAINT fk_stock_item_prescription FOREIGN KEY (prescription_item_id) REFERENCES prescription_item(id),
    CONSTRAINT fk_stock_item_source FOREIGN KEY (source_item_id) REFERENCES stock_document_item(id),
    CONSTRAINT ck_stock_item_cost CHECK (unit_cost IS NULL OR unit_cost >= 0),
    INDEX ix_stock_item_batch (batch_id),
    INDEX ix_stock_item_prescription (prescription_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hospital_config (
    id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    address TEXT NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(150) NULL,
    logo_url TEXT NULL,
    description TEXT NULL,
    advance_booking_days INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    CONSTRAINT ck_hospital_singleton CHECK (id = 1),
    CONSTRAINT ck_hospital_booking_days CHECK (advance_booking_days > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Audit FKs are added after all tables exist; NULL is allowed for bootstrap/system writes.
ALTER TABLE user_account
    ADD CONSTRAINT fk_user_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_user_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE patient
    ADD CONSTRAINT fk_patient_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_patient_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE specialty
    ADD CONSTRAINT fk_specialty_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_specialty_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE clinic_room
    ADD CONSTRAINT fk_room_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_room_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE doctor_profile
    ADD CONSTRAINT fk_doctor_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_doctor_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE clinic_slot
    ADD CONSTRAINT fk_slot_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_slot_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE appointment
    ADD CONSTRAINT fk_appointment_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_appointment_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE medical_visit
    ADD CONSTRAINT fk_visit_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_visit_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE medicine
    ADD CONSTRAINT fk_medicine_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_medicine_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE medicine_batch
    ADD CONSTRAINT fk_batch_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_batch_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE supplier
    ADD CONSTRAINT fk_supplier_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_supplier_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE prescription
    ADD CONSTRAINT fk_prescription_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_prescription_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE prescription_item
    ADD CONSTRAINT fk_prescription_item_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_prescription_item_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE purchase_request
    ADD CONSTRAINT fk_purchase_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_purchase_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE purchase_request_item
    ADD CONSTRAINT fk_purchase_item_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_purchase_item_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE stock_document
    ADD CONSTRAINT fk_stock_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_stock_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE stock_document_item
    ADD CONSTRAINT fk_stock_item_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_stock_item_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
ALTER TABLE hospital_config
    ADD CONSTRAINT fk_hospital_created_by FOREIGN KEY (created_by) REFERENCES user_account(id),
    ADD CONSTRAINT fk_hospital_updated_by FOREIGN KEY (updated_by) REFERENCES user_account(id);
