-- Deactivating a member keeps their history (reports, comments, timeline)
-- but ends their access. Existing accounts are active.
ALTER TABLE app_users
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN deactivated_at TIMESTAMP WITH TIME ZONE;

-- Emails are stored lowercase; make uniqueness case-insensitive too, so
-- "Ann@x.com" and "ann@x.com" can never both exist.
CREATE UNIQUE INDEX ux_app_users_email_lower ON app_users (LOWER(email));
