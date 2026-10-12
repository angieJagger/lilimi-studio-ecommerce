ALTER TABLE project_inquiries
    DROP CONSTRAINT project_inquiries_type_check;

ALTER TABLE project_inquiries
    ADD CONSTRAINT project_inquiries_type_check
    CHECK (
        project_type IN (
            'embroideredProduct',
            'digitizing',
            'website',
            'uxUi',
            'wordpress',
            'graphicDesign',
            'other'
        )
    );
