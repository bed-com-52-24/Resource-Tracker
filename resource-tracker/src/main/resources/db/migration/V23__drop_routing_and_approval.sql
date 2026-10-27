DROP TABLE routing_rule_audit;
DROP TABLE routing_rule_lock;
DROP TABLE routing_rules;
DROP TABLE request_line_items;
DROP TABLE audit_logs;

ALTER TABLE resource_requests
    DROP CHECK chk_request_status,
    ADD CONSTRAINT chk_request_status
        CHECK (status IN ('PENDING', 'QUERIED', 'APPROVED', 'REJECTED', 'CANCELLED'));

ALTER TABLE resource_requests
    DROP COLUMN version,
    DROP COLUMN edited_after_query,
    DROP COLUMN queried_at;
