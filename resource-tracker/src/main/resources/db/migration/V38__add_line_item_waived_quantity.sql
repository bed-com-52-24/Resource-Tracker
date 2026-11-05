ALTER TABLE resource_request_line_items
    ADD COLUMN waived_quantity INT NOT NULL DEFAULT 0;
