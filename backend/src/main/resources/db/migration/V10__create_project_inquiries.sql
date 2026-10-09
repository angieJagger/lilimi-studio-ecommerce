CREATE TABLE project_inquiries (
  id UUID PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'new',
  language VARCHAR(2) NOT NULL,
  customer_name VARCHAR(150) NOT NULL,
  customer_email VARCHAR(254) NOT NULL,
  project_type VARCHAR(32) NOT NULL,
  description VARCHAR(5000) NOT NULL,
  inspiration_url VARCHAR(2048),
  product_id VARCHAR(100),

  CONSTRAINT project_inquiries_status_check
    CHECK (status IN ('new', 'in_progress', 'answered', 'closed')),

  CONSTRAINT project_inquiries_language_check
    CHECK (language IN ('pl', 'en')),

  CONSTRAINT project_inquiries_type_check
    CHECK (
      project_type IN (
        'embroideredProduct',
        'digitizing',
        'website',
        'other'
      )
    ),

  CONSTRAINT project_inquiries_name_check
    CHECK (btrim(customer_name) <> ''),

  CONSTRAINT project_inquiries_email_check
    CHECK (btrim(customer_email) <> ''),

  CONSTRAINT project_inquiries_description_check
    CHECK (btrim(description) <> '')
);

CREATE INDEX project_inquiries_created_at_idx
  ON project_inquiries (created_at DESC, id DESC);
