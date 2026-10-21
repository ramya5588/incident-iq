ALTER TABLE incidents
    ADD COLUMN ai_suggested_category VARCHAR(100),
    ADD COLUMN ai_reasoning TEXT,
    ADD COLUMN ai_triaged_at TIMESTAMP WITH TIME ZONE;