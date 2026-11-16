-- An incident closed as a duplicate points at the main incident.
ALTER TABLE incidents
    ADD COLUMN duplicate_of UUID REFERENCES incidents(id) ON DELETE SET NULL,
    ADD CONSTRAINT incidents_not_own_duplicate CHECK (duplicate_of IS NULL OR duplicate_of <> id);

CREATE INDEX idx_incidents_duplicate_of ON incidents (duplicate_of) WHERE duplicate_of IS NOT NULL;

ALTER TABLE incident_events DROP CONSTRAINT incident_events_type_check;
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
            'ATTACHMENT_REMOVED',
            'SATISFACTION_RATED',
            'MERGED_INTO',
            'DUPLICATE_MERGED',
            'UNMERGED',
            'DUPLICATE_UNMERGED'
        ));
