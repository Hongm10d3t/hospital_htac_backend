ALTER TABLE purchase_request
    ADD COLUMN review_note TEXT NULL AFTER reviewed_at,
    ADD COLUMN close_note TEXT NULL AFTER closed_at;
