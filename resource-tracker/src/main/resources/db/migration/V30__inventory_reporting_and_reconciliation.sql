CREATE TABLE resource_reconciliations (
    reconciliation_id INT NOT NULL AUTO_INCREMENT,
    resource_id INT NOT NULL,
    system_quantity INT NOT NULL,
    physical_count INT NOT NULL,
    variance INT NOT NULL,
    adjustment_applied BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(500) NOT NULL,
    actor_id INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (reconciliation_id),
    CONSTRAINT fk_reconciliation_resource FOREIGN KEY (resource_id) REFERENCES resources(id),
    CONSTRAINT fk_reconciliation_actor FOREIGN KEY (actor_id) REFERENCES users(user_id),
    CONSTRAINT chk_reconciliation_quantities CHECK (system_quantity >= 0 AND physical_count >= 0),
    INDEX idx_reconciliation_resource_time (resource_id, created_at)
);

CREATE INDEX idx_stock_issue_resource_request ON stock_issues (resource_id, request_id);
CREATE INDEX idx_request_line_resource ON resource_request_line_items (resource_id);
