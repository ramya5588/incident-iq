-- Which notifications each person also gets by email. In-app notifications
-- are unaffected. Defaults: assignments and missed deadlines on, the
-- chattier comment and status emails off.
ALTER TABLE app_users
    ADD COLUMN email_on_assigned BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN email_on_commented BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN email_on_status_changed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN email_on_sla_breached BOOLEAN NOT NULL DEFAULT TRUE;
