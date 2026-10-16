-- Add idempotency metadata to the existing table.
ALTER TABLE resource_request_idempotency
    ADD COLUMN request_hash CHAR(64) NULL,
    ADD COLUMN response_status SMALLINT NULL,
    ADD COLUMN response_body LONGTEXT NULL,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    ADD COLUMN updated_at TIMESTAMP NOT NULL
        DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    ADD COLUMN expires_at TIMESTAMP NULL;

-- Existing records predate the new idempotency contract.
-- Expire them immediately so they cannot be replayed accidentally.
UPDATE resource_request_idempotency
SET expires_at = created_at,
    status = CASE
        WHEN request_id IS NOT NULL THEN 'COMPLETED'
        ELSE 'FAILED'
    END;

-- New records must have a hash and expiry.
ALTER TABLE resource_request_idempotency
    MODIFY COLUMN request_hash CHAR(64) NOT NULL,
    MODIFY COLUMN expires_at TIMESTAMP NOT NULL;

CREATE INDEX idx_request_idempotency_expiry
    ON resource_request_idempotency (expires_at, status);

-- Separate audit trail for idempotency transitions.
CREATE TABLE resource_request_component_audit (
    audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    previous_state VARCHAR(20) NULL,
    new_state VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_component_audit_idempotency
        FOREIGN KEY (idempotency_id)
        REFERENCES resource_request_idempotency(idempotency_id)
);

-- Ensure the yearly counter cannot become negative.
ALTER TABLE resource_request_sequences
    MODIFY COLUMN last_sequence INT NOT NULL DEFAULT 0;
