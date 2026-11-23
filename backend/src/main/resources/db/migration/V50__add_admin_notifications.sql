CREATE TABLE admin_notifications (
    id UUID PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    resource_id UUID NOT NULL,
    channel VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMP WITH TIME ZONE,
    claim_token UUID,
    last_error_code VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uq_admin_notifications_event_channel
        UNIQUE (event_type, resource_id, channel),

    CONSTRAINT chk_admin_notifications_event
        CHECK (event_type IN ('INQUIRY_CREATED', 'ORDER_PAID')),

    CONSTRAINT chk_admin_notifications_channel
        CHECK (channel IN ('TELEGRAM', 'EMAIL')),

    CONSTRAINT chk_admin_notifications_status
        CHECK (status IN (
            'PENDING',
            'PROCESSING',
            'SENT',
            'FAILED',
            'SKIPPED'
        )),

    CONSTRAINT chk_admin_notifications_attempts
        CHECK (attempts >= 0)
);

CREATE INDEX idx_admin_notifications_pending
    ON admin_notifications (available_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_admin_notifications_processing
    ON admin_notifications (lease_until)
    WHERE status = 'PROCESSING';
