ALTER TABLE resource_requests
    ADD COLUMN priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    ADD CONSTRAINT chk_resource_request_priority
        CHECK (priority IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'));
