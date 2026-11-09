CREATE TABLE audit_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actor_id INT NULL,
    actor_role VARCHAR(64) NULL,
    action VARCHAR(48) NOT NULL,
    item_type VARCHAR(64) NOT NULL,
    item_id VARCHAR(128) NOT NULL,
    previous_state JSON NULL,
    new_state JSON NULL,
    comment VARCHAR(500) NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(512) NULL,
    changed_fields JSON NULL,
    correlation_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_entry_actor FOREIGN KEY (actor_id) REFERENCES users(user_id),
    INDEX idx_audit_entry_occurred_at (occurred_at),
    INDEX idx_audit_entry_actor_time (actor_id, occurred_at),
    INDEX idx_audit_entry_action_time (action, occurred_at),
    INDEX idx_audit_entry_item_time (item_type, item_id, occurred_at),
    INDEX idx_audit_entry_correlation_id (correlation_id)
);

CREATE TRIGGER trg_audit_entry_no_update
BEFORE UPDATE ON audit_entry
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_entry is append-only';

CREATE TRIGGER trg_audit_entry_no_delete
BEFORE DELETE ON audit_entry
FOR EACH ROW
SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'audit_entry is append-only';
