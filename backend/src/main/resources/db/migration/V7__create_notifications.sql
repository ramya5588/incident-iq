-- In-app notifications, one row per recipient.
CREATE TABLE notifications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    incident_id UUID NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,
    message VARCHAR(500) NOT NULL,
    actor_id UUID REFERENCES app_users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT notifications_type_check
        CHECK (type IN ('ASSIGNED', 'COMMENTED', 'STATUS_CHANGED', 'SLA_BREACHED'))
);

-- "My notifications, newest first" and "my unread count".
CREATE INDEX idx_notifications_recipient ON notifications (recipient_id, id DESC);
CREATE INDEX idx_notifications_unread ON notifications (recipient_id) WHERE read_at IS NULL;

-- When each SLA breach was announced, so it's announced once.
ALTER TABLE incidents
    ADD COLUMN response_breach_notified_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN resolution_breach_notified_at TIMESTAMP WITH TIME ZONE;

-- Deadlines already breached before notifications existed are treated as
-- announced, so the first run doesn't flood everyone with old breaches.
UPDATE incidents
SET response_breach_notified_at = CURRENT_TIMESTAMP
WHERE first_responded_at IS NULL
  AND respond_by <= CURRENT_TIMESTAMP
  AND status IN ('OPEN', 'IN_PROGRESS');

UPDATE incidents
SET resolution_breach_notified_at = CURRENT_TIMESTAMP
WHERE resolve_by <= CURRENT_TIMESTAMP
  AND status IN ('OPEN', 'IN_PROGRESS');
