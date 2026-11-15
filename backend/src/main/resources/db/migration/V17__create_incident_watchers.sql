-- People following an incident. They get its comments, status changes and
-- missed deadlines, like the reporter and assignee do.
CREATE TABLE incident_watchers (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT incident_watchers_unique UNIQUE (incident_id, user_id)
);

-- "Incidents I watch" for the list filter.
CREATE INDEX idx_incident_watchers_user ON incident_watchers (user_id, incident_id);
