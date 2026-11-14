-- Files attached to incidents (screenshots, logs, PDFs). Kept in the
-- database so backups cover them; the type is detected from the content.
CREATE TABLE incident_attachments (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    uploaded_by UUID NOT NULL REFERENCES app_users (id),
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes INTEGER NOT NULL CHECK (size_bytes > 0),
    data BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_incident_attachments_incident ON incident_attachments (incident_id, created_at);

-- Timeline entries for adding and removing attachments.
ALTER TABLE incident_events
    DROP CONSTRAINT incident_events_type_check;

ALTER TABLE incident_events
    ADD CONSTRAINT incident_events_type_check
        CHECK (type IN (
            'CREATED',
            'STATUS_CHANGED',
            'ASSIGNEE_CHANGED',
            'AI_TRIAGED',
            'COMMENT',
            'TITLE_CHANGED',
            'DESCRIPTION_EDITED',
            'PRIORITY_CHANGED',
            'CATEGORY_CHANGED',
            'ATTACHMENT_ADDED',
            'ATTACHMENT_REMOVED'
        ));
