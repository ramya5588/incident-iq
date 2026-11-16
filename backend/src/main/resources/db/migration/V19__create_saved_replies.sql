-- Reusable replies agents insert into comments. owner_id NULL means shared
-- with the whole team (admins manage those); otherwise it's that person's own.
CREATE TABLE saved_replies (
    id UUID PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    body TEXT NOT NULL,
    owner_id UUID REFERENCES app_users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT saved_replies_body_length CHECK (char_length(body) BETWEEN 1 AND 2000)
);

CREATE INDEX idx_saved_replies_owner ON saved_replies (owner_id);
