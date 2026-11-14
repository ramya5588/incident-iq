-- Someone changing their own sign-in email: the change applies only once
-- they confirm it from the new address. Only a SHA-256 hash of each
-- confirmation token is stored.
CREATE TABLE email_change_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    new_email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_email_change_requests_user ON email_change_requests (user_id, created_at);
