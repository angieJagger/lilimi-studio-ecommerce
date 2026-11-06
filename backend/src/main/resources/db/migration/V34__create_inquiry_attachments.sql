CREATE TABLE inquiry_attachments (
  id UUID PRIMARY KEY,
  inquiry_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  original_filename VARCHAR(255) NOT NULL,
  storage_key VARCHAR(255) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  byte_size BIGINT NOT NULL,

  CONSTRAINT fk_inquiry_attachments_inquiry
    FOREIGN KEY (inquiry_id)
    REFERENCES project_inquiries (id),

  CONSTRAINT uq_inquiry_attachments_storage_key
    UNIQUE (storage_key),

  CONSTRAINT inquiry_attachments_filename_check
    CHECK (btrim(original_filename) <> ''),

  CONSTRAINT inquiry_attachments_storage_key_check
    CHECK (btrim(storage_key) <> ''),

  CONSTRAINT inquiry_attachments_content_type_check
    CHECK (
      content_type IN (
        'image/jpeg',
        'image/png',
        'image/webp',
        'application/pdf'
      )
    ),

  CONSTRAINT inquiry_attachments_size_check
    CHECK (byte_size > 0 AND byte_size <= 10485760)
);

CREATE INDEX inquiry_attachments_inquiry_idx
  ON inquiry_attachments (inquiry_id, created_at, id);
