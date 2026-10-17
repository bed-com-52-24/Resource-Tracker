
-- V8: Extend the routing_rules table created by V1 and add audit history.

ALTER TABLE routing_rules
    ADD COLUMN priority INT NOT NULL DEFAULT 0,
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at TIMESTAMP NULL,
    ADD COLUMN created_by VARCHAR(50) NOT NULL DEFAULT '',
    ADD COLUMN updated_by VARCHAR(50) NOT NULL DEFAULT '',
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    ADD CONSTRAINT chk_routing_rule_priority CHECK (priority >= 0),
    ADD INDEX idx_routing_active_deleted (active, deleted);

-- Audit history for routing-rule changes
CREATE TABLE routing_rule_audit (
    audit_id BIGINT NOT NULL AUTO_INCREMENT,
    routing_rule_id INT NOT NULL,
    actor VARCHAR(50) NOT NULL,
    action VARCHAR(30) NOT NULL,
    old_values JSON NULL,
    new_values JSON NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (audit_id),

    CONSTRAINT fk_routing_audit_rule
        FOREIGN KEY (routing_rule_id)
        REFERENCES routing_rules(routing_rule_id),

    INDEX idx_routing_audit_time (changed_at)
);

-- Transaction lock used to prevent concurrent duplicate activations
CREATE TABLE routing_rule_lock (
    lock_id INT NOT NULL PRIMARY KEY
);

INSERT INTO routing_rule_lock (lock_id) VALUES (1);
