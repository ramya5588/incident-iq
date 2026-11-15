-- Internal notes: comments only agents and admins can see.
ALTER TABLE incident_events
    ADD COLUMN internal BOOLEAN NOT NULL DEFAULT FALSE;

-- Only comments can be internal; system events are always visible.
ALTER TABLE incident_events
    ADD CONSTRAINT incident_events_internal_check
        CHECK (NOT internal OR type = 'COMMENT');
