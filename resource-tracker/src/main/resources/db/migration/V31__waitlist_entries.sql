ALTER TABLE resource_requests
    DROP CHECK chk_request_status,
    ADD CONSTRAINT chk_request_status CHECK (
        status IN ('PENDING', 'QUERIED', 'APPROVED', 'REJECTED', 'WAITLISTED', 'CANCELLED')
    );

CREATE TABLE waitlist_entries (
    waitlist_entry_id INT NOT NULL AUTO_INCREMENT,
    request_id INT NOT NULL,
    line_item_id INT NOT NULL,
    resource_id INT NOT NULL,
    quantity INT NOT NULL,
    reserved_quantity INT NOT NULL DEFAULT 0,
    issued_quantity INT NOT NULL DEFAULT 0,
    status VARCHAR(12) NOT NULL DEFAULT 'WAITING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (waitlist_entry_id),
    CONSTRAINT uq_waitlist_line UNIQUE (line_item_id),
    CONSTRAINT fk_waitlist_request FOREIGN KEY (request_id) REFERENCES resource_requests(request_id),
    CONSTRAINT fk_waitlist_line FOREIGN KEY (line_item_id) REFERENCES resource_request_line_items(line_item_id),
    CONSTRAINT fk_waitlist_resource FOREIGN KEY (resource_id) REFERENCES resources(id),
    CONSTRAINT chk_waitlist_quantity CHECK (quantity > 0),
    CONSTRAINT chk_waitlist_reserved CHECK (reserved_quantity >= 0 AND reserved_quantity <= quantity),
    CONSTRAINT chk_waitlist_issued CHECK (issued_quantity >= 0 AND issued_quantity <= reserved_quantity),
    CONSTRAINT chk_waitlist_status CHECK (status IN ('WAITING', 'FULFILLABLE', 'FULFILLED', 'CANCELLED')),
    INDEX idx_waitlist_fifo (resource_id, status, created_at, waitlist_entry_id)
);
