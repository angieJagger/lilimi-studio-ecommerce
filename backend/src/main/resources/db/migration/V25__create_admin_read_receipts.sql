CREATE TABLE admin_order_reads (
    administrator_id UUID NOT NULL
        REFERENCES user_accounts(id) ON DELETE CASCADE,

    order_id UUID NOT NULL
        REFERENCES orders(id) ON DELETE CASCADE,

    read_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (administrator_id, order_id)
);

CREATE INDEX idx_admin_order_reads_order_id
    ON admin_order_reads (order_id);


CREATE TABLE admin_inquiry_reads (
    administrator_id UUID NOT NULL
        REFERENCES user_accounts(id) ON DELETE CASCADE,

    inquiry_id UUID NOT NULL
        REFERENCES project_inquiries(id) ON DELETE CASCADE,

    read_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (administrator_id, inquiry_id)
);

CREATE INDEX idx_admin_inquiry_reads_inquiry_id
    ON admin_inquiry_reads (inquiry_id);
