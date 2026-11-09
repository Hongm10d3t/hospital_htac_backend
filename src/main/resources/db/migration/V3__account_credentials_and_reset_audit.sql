ALTER TABLE user_account
    ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_user_credential_version CHECK (credential_version >= 0);

CREATE TABLE account_password_reset_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    target_user_id BIGINT NOT NULL,
    admin_user_id BIGINT NOT NULL,
    verified_via VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_reset_target FOREIGN KEY (target_user_id) REFERENCES user_account(id),
    CONSTRAINT fk_reset_admin FOREIGN KEY (admin_user_id) REFERENCES user_account(id),
    CONSTRAINT ck_reset_verified_via CHECK (verified_via IN ('IN_PERSON', 'PHONE_CALLBACK')),
    INDEX ix_reset_target_time (target_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
