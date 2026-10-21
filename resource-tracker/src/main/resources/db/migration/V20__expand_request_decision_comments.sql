ALTER TABLE audit_logs
    MODIFY COLUMN comment VARCHAR(1000) NULL;

ALTER TABLE request_line_items
    MODIFY COLUMN decision_comment VARCHAR(1000) NULL;
