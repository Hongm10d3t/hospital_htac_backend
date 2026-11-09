ALTER TABLE patient
    ADD COLUMN registration_token_hash VARCHAR(64) NULL,
    ADD COLUMN registration_token_expires_at DATETIME(6) NULL,
    ADD CONSTRAINT ck_patient_registration_token_pair CHECK (
        (registration_token_hash IS NULL AND registration_token_expires_at IS NULL)
        OR (registration_token_hash IS NOT NULL AND registration_token_expires_at IS NOT NULL)
    );

CREATE INDEX ix_patient_name_birth ON patient (full_name, date_of_birth);
