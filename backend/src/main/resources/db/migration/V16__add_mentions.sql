-- @mentions: a notification type, and whether it's also emailed (on by default).
ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
        CHECK (type IN ('ASSIGNED', 'COMMENTED', 'MENTIONED', 'STATUS_CHANGED', 'SLA_BREACHED'));

ALTER TABLE app_users
    ADD COLUMN email_on_mentioned BOOLEAN NOT NULL DEFAULT TRUE;
