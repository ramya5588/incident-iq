-- Failed sign-ins in the current window, and a temporary lock after too many.
ALTER TABLE app_users
    ADD COLUMN failed_sign_ins INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN first_failed_sign_in_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE;
