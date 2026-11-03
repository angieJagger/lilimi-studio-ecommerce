ALTER TABLE digital_download_emails
    ADD COLUMN last_requested_at TIMESTAMPTZ;

UPDATE digital_download_emails
SET last_requested_at = COALESCE(sent_at, created_at);

ALTER TABLE digital_download_emails
    ALTER COLUMN last_requested_at SET NOT NULL;

ALTER TABLE digital_download_emails
    ALTER COLUMN last_requested_at SET DEFAULT CURRENT_TIMESTAMP;
