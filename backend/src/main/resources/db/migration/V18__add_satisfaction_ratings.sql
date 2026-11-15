-- The reporter's rating of how an incident was handled, 1 (poor) to 5 (great).
ALTER TABLE incidents
    ADD COLUMN satisfaction_score INTEGER
        CONSTRAINT incidents_satisfaction_score_check CHECK (satisfaction_score BETWEEN 1 AND 5),
    ADD COLUMN satisfaction_comment VARCHAR(1000),
    ADD COLUMN rated_at TIMESTAMP WITH TIME ZONE;

-- Ratings in a date range, for Reports.
CREATE INDEX idx_incidents_rated_at ON incidents (rated_at) WHERE rated_at IS NOT NULL;

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
            'SATISFACTION_RATED'
        ));
