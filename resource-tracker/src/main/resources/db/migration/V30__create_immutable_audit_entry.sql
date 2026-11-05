CREATE TABLE IF NOT EXISTS audit_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    occurred_at DATETIME(6) NOT NULL,
    actor_id INT,
    actor_role VARCHAR(64),
    action VARCHAR(48) NOT NULL,
    item_type VARCHAR(64) NOT NULL,
    item_id VARCHAR(128) NOT NULL,
    previous_state JSON,
    new_state JSON,
    comment VARCHAR(500),
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    changed_fields JSON,
    correlation_id VARCHAR(64) NOT NULL
);

CREATE TRIGGER trg_audit_entry_no_update
BEFORE UPDATE ON audit_entry
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_entry is append-only';

CREATE TRIGGER trg_audit_entry_no_delete
BEFORE DELETE ON audit_entry
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_entry is append-only';
