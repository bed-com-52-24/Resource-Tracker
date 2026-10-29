ALTER TABLE resource_requests
    DROP CHECK chk_request_status,
    ADD CONSTRAINT chk_request_status CHECK (status IN (
        'PENDING',
        'QUERIED',
        'APPROVED',
        'COMPLETED',
        'REJECTED',
        'WAITLISTED',
        'CANCELLED'
    ));
