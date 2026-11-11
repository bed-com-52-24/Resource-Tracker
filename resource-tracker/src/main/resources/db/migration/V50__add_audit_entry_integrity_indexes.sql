ALTER TABLE audit_entry
    ADD CONSTRAINT fk_audit_entry_actor
        FOREIGN KEY (actor_id) REFERENCES users(user_id),
    ADD INDEX idx_audit_entry_occurred_at (occurred_at),
    ADD INDEX idx_audit_entry_actor_time (actor_id, occurred_at),
    ADD INDEX idx_audit_entry_action_time (action, occurred_at),
    ADD INDEX idx_audit_entry_item_time (item_type, item_id, occurred_at),
    ADD INDEX idx_audit_entry_correlation_id (correlation_id);
