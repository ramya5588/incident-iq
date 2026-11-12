-- SLA deadlines. respond_by / resolve_by are computed from created_at and
-- the priority's targets; first_responded_at is when someone first assigned
-- the incident or moved it out of OPEN.
ALTER TABLE incidents
    ADD COLUMN respond_by TIMESTAMP WITH TIME ZONE,
    ADD COLUMN resolve_by TIMESTAMP WITH TIME ZONE,
    ADD COLUMN first_responded_at TIMESTAMP WITH TIME ZONE;

-- Backfill existing incidents with the default targets
-- (the same values as application.properties at the time of this migration).
UPDATE incidents
SET respond_by = created_at + CASE priority
        WHEN 'CRITICAL' THEN INTERVAL '15 minutes'
        WHEN 'HIGH' THEN INTERVAL '1 hour'
        WHEN 'MEDIUM' THEN INTERVAL '4 hours'
        ELSE INTERVAL '8 hours'
    END,
    resolve_by = created_at + CASE priority
        WHEN 'CRITICAL' THEN INTERVAL '4 hours'
        WHEN 'HIGH' THEN INTERVAL '8 hours'
        WHEN 'MEDIUM' THEN INTERVAL '24 hours'
        ELSE INTERVAL '3 days'
    END;

-- First response: the earliest status or assignee change on the timeline.
UPDATE incidents i
SET first_responded_at = (
    SELECT MIN(e.created_at)
    FROM incident_events e
    WHERE e.incident_id = i.id
      AND e.type IN ('STATUS_CHANGED', 'ASSIGNEE_CHANGED')
);

-- Picked up before the timeline existed: best available time is updated_at.
UPDATE incidents
SET first_responded_at = updated_at
WHERE first_responded_at IS NULL
  AND (status <> 'OPEN' OR assigned_to IS NOT NULL);

ALTER TABLE incidents
    ALTER COLUMN respond_by SET NOT NULL,
    ALTER COLUMN resolve_by SET NOT NULL;

-- Supports the dashboard "breached" count and the overdue filter.
CREATE INDEX idx_incidents_status_resolve_by ON incidents (status, resolve_by);
