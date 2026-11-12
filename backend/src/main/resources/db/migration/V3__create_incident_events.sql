-- Activity timeline for incidents: system events (created, status/assignee
-- changes, AI triage) and user comments.
CREATE TABLE incident_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,
    from_value VARCHAR(200),
    to_value VARCHAR(200),
    message TEXT,
    actor_id UUID REFERENCES app_users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT incident_events_type_check
        CHECK (type IN (
            'CREATED',
            'STATUS_CHANGED',
            'ASSIGNEE_CHANGED',
            'AI_TRIAGED',
            'COMMENT'
        )),

    CONSTRAINT incident_events_comment_message_check
        CHECK (type <> 'COMMENT' OR message IS NOT NULL)
);

CREATE INDEX idx_incident_events_incident_id
    ON incident_events(incident_id, id);

-- Give existing incidents a "created" entry so their timeline isn't empty.
INSERT INTO incident_events (incident_id, type, actor_id, created_at)
SELECT id, 'CREATED', created_by, created_at
FROM incidents;
