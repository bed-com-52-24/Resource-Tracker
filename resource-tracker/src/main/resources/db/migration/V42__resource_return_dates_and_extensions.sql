ALTER TABLE resource_request_line_items
    ADD COLUMN requested_duration_days INT NULL,
    ADD COLUMN approved_duration_days INT NULL,
    ADD CONSTRAINT chk_line_requested_duration CHECK (requested_duration_days IS NULL OR requested_duration_days > 0),
    ADD CONSTRAINT chk_line_approved_duration CHECK (approved_duration_days IS NULL OR approved_duration_days > 0);

ALTER TABLE stock_issues
    ADD COLUMN expected_return_date DATE NULL,
    ADD COLUMN reminder_7_days_sent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reminder_2_days_sent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reminder_due_day_sent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD INDEX idx_stock_issues_return_reminders (expected_return_date, returned_quantity, quantity);

CREATE TABLE stock_return_extensions (
    extension_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    issue_id INT NOT NULL,
    requested_by INT NOT NULL,
    requested_return_date DATE NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    decided_by INT NULL,
    decided_at TIMESTAMP NULL,
    decision_comment VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_return_extension_issue FOREIGN KEY (issue_id) REFERENCES stock_issues(issue_id),
    CONSTRAINT fk_return_extension_requester FOREIGN KEY (requested_by) REFERENCES users(user_id),
    CONSTRAINT fk_return_extension_decider FOREIGN KEY (decided_by) REFERENCES users(user_id),
    CONSTRAINT chk_return_extension_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    INDEX idx_return_extension_issue_status (issue_id, status)
);
