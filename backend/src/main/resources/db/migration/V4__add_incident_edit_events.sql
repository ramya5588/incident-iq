-- Allow timeline entries for editing an incident's details.
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
            'CATEGORY_CHANGED'
        ));
