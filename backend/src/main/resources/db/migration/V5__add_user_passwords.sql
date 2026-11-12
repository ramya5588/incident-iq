-- Sign-in: BCrypt password hash per user. Nullable so existing accounts
-- keep working; a user without a password simply cannot sign in yet.
ALTER TABLE app_users
    ADD COLUMN password_hash VARCHAR(100);
