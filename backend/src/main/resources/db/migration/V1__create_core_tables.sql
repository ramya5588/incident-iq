CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT app_users_role_check
        CHECK (role IN ('USER', 'AGENT', 'ADMIN'))
);

CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    incident_number BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    category VARCHAR(100),
    created_by UUID NOT NULL REFERENCES app_users(id),
    assigned_to UUID REFERENCES app_users(id),
    ai_summary TEXT,
    ai_suggested_priority VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT incidents_status_check
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),

    CONSTRAINT incidents_priority_check
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),

    CONSTRAINT incidents_ai_priority_check
        CHECK (
            ai_suggested_priority IS NULL
            OR ai_suggested_priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
        )
);

CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incidents_priority ON incidents(priority);
CREATE INDEX idx_incidents_created_by ON incidents(created_by);