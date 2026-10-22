-- Phase 1 request lifecycle: derive ownership and decisions server-side, and
-- preserve every workflow comment/event in an append-only history table.
ALTER TABLE resource_requests
    MODIFY COLUMN vendor_id INT NULL,
    MODIFY COLUMN assigned_manager_id INT NULL,
    ADD COLUMN needs_reassignment BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN required_date DATE NULL,
    ADD COLUMN decided_by INT NULL,
    ADD COLUMN decided_at TIMESTAMP NULL,
    ADD COLUMN decision_reason VARCHAR(500) NULL,
    ADD COLUMN cancelled_by INT NULL,
    ADD COLUMN cancelled_at TIMESTAMP NULL,
    ADD COLUMN cancel_reason VARCHAR(500) NULL,
    ADD COLUMN unique_key VARCHAR(64) NULL,
    ADD CONSTRAINT fk_request_decided_by FOREIGN KEY (decided_by) REFERENCES users(user_id),
    ADD CONSTRAINT fk_request_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users(user_id),
    ADD UNIQUE INDEX uq_resource_requests_unique_key (unique_key);

ALTER TABLE resource_request_line_items
    ADD COLUMN resource_id INT NULL,
    ADD COLUMN fulfillment_preference VARCHAR(10) NOT NULL DEFAULT 'INTERNAL',
    ADD COLUMN unit_of_measure VARCHAR(50) NULL,
    ADD COLUMN approved_quantity INT NULL,
    ADD CONSTRAINT fk_request_line_resource FOREIGN KEY (resource_id) REFERENCES resources(id),
    ADD CONSTRAINT chk_request_line_fulfillment CHECK (fulfillment_preference IN ('INTERNAL', 'EXTERNAL'));

ALTER TABLE resource_request_line_items
    DROP CHECK chk_line_item_price,
    ADD CONSTRAINT chk_line_item_price CHECK (unit_price >= 0);

CREATE TABLE request_history (
    history_id INT NOT NULL AUTO_INCREMENT,
    request_id INT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NULL,
    actor_id INT NOT NULL,
    line_item_id INT NULL,
    quantity INT NULL,
    comment VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (history_id),
    CONSTRAINT fk_request_history_request FOREIGN KEY (request_id) REFERENCES resource_requests(request_id),
    CONSTRAINT fk_request_history_actor FOREIGN KEY (actor_id) REFERENCES users(user_id),
    CONSTRAINT fk_request_history_line FOREIGN KEY (line_item_id) REFERENCES resource_request_line_items(line_item_id),
    INDEX idx_request_history_request (request_id, created_at)
);

ALTER TABLE resource_requests
    DROP CHECK chk_request_status,
    ADD CONSTRAINT chk_request_status CHECK (
        status IN ('PENDING', 'QUERIED', 'APPROVED', 'REJECTED', 'CANCELLED')
    );

-- These legacy tables are already removed by V23 on a clean migration path.
DROP TABLE IF EXISTS resource_request_component_audit;
DROP TABLE IF EXISTS resource_request_idempotency;
DROP TABLE IF EXISTS request_line_items;
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS routing_rule_audit;
DROP TABLE IF EXISTS routing_rule_lock;
DROP TABLE IF EXISTS routing_rules;

-- Phase 2-4 stock and procurement tables. Resource.quantity remains physical stock.
ALTER TABLE resources
    ADD COLUMN unavailable_quantity INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_resources_unavailable_quantity CHECK (unavailable_quantity >= 0);

CREATE TABLE stock_reservations (
    reservation_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    resource_id INT NOT NULL,
    request_id INT NOT NULL,
    line_item_id INT NOT NULL,
    team_id INT NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NULL,
    created_by INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP NULL,
    close_reason VARCHAR(255) NULL,
    CONSTRAINT fk_reservation_resource FOREIGN KEY (resource_id) REFERENCES resources(id),
    CONSTRAINT fk_reservation_request FOREIGN KEY (request_id) REFERENCES resource_requests(request_id),
    CONSTRAINT fk_reservation_line FOREIGN KEY (line_item_id) REFERENCES resource_request_line_items(line_item_id),
    CONSTRAINT fk_reservation_team FOREIGN KEY (team_id) REFERENCES teams(team_id),
    CONSTRAINT fk_reservation_creator FOREIGN KEY (created_by) REFERENCES users(user_id),
    CONSTRAINT chk_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT chk_reservation_status CHECK (status IN ('ACTIVE', 'CONSUMED', 'RELEASED', 'EXPIRED')),
    INDEX idx_reservation_resource_status (resource_id, status),
    INDEX idx_reservation_request_line_status (request_id, line_item_id, status)
);

CREATE TABLE stock_issues (
    issue_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id INT NOT NULL,
    line_item_id INT NOT NULL,
    resource_id INT NOT NULL,
    team_id INT NOT NULL,
    reservation_id INT NULL,
    quantity INT NOT NULL,
    returned_quantity INT NOT NULL DEFAULT 0,
    issued_by INT NOT NULL,
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_issue_request FOREIGN KEY (request_id) REFERENCES resource_requests(request_id),
    CONSTRAINT fk_issue_line FOREIGN KEY (line_item_id) REFERENCES resource_request_line_items(line_item_id),
    CONSTRAINT fk_issue_resource FOREIGN KEY (resource_id) REFERENCES resources(id),
    CONSTRAINT fk_issue_team FOREIGN KEY (team_id) REFERENCES teams(team_id),
    CONSTRAINT fk_issue_reservation FOREIGN KEY (reservation_id) REFERENCES stock_reservations(reservation_id),
    CONSTRAINT fk_issue_actor FOREIGN KEY (issued_by) REFERENCES users(user_id),
    CONSTRAINT chk_issue_quantity CHECK (quantity > 0 AND returned_quantity >= 0 AND returned_quantity <= quantity),
    INDEX idx_stock_issue_request_line (request_id, line_item_id)
);

CREATE TABLE stock_returns (
    return_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    issue_id INT NOT NULL,
    quantity INT NOT NULL,
    condition_reported VARCHAR(12) NULL,
    returned_by INT NOT NULL,
    returned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    verified_by INT NULL,
    verified_at TIMESTAMP NULL,
    note VARCHAR(500) NULL,
    CONSTRAINT fk_return_issue FOREIGN KEY (issue_id) REFERENCES stock_issues(issue_id),
    CONSTRAINT fk_return_actor FOREIGN KEY (returned_by) REFERENCES users(user_id),
    CONSTRAINT fk_return_verifier FOREIGN KEY (verified_by) REFERENCES users(user_id),
    CONSTRAINT chk_return_quantity CHECK (quantity > 0),
    CONSTRAINT chk_return_status CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    CONSTRAINT chk_return_condition CHECK (condition_reported IS NULL OR condition_reported IN ('USABLE', 'DAMAGED', 'LOST', 'QUARANTINED'))
);

CREATE TABLE stock_movements (
    movement_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    resource_id INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    request_id INT NULL,
    issue_id INT NULL,
    return_id INT NULL,
    actor_id INT NOT NULL,
    reason VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_movement_resource FOREIGN KEY (resource_id) REFERENCES resources(id),
    CONSTRAINT fk_movement_request FOREIGN KEY (request_id) REFERENCES resource_requests(request_id),
    CONSTRAINT fk_movement_issue FOREIGN KEY (issue_id) REFERENCES stock_issues(issue_id),
    CONSTRAINT fk_movement_return FOREIGN KEY (return_id) REFERENCES stock_returns(return_id),
    CONSTRAINT fk_movement_actor FOREIGN KEY (actor_id) REFERENCES users(user_id),
    CONSTRAINT chk_movement_type CHECK (type IN ('ISSUE', 'RETURN_USABLE', 'RETURN_UNAVAILABLE', 'CORRECTION', 'OPENING')),
    CONSTRAINT chk_movement_quantity CHECK (quantity <> 0),
    INDEX idx_stock_movement_resource_time (resource_id, created_at)
);

CREATE TABLE procurements (
    procurement_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id INT NOT NULL,
    line_item_id INT NOT NULL,
    vendor_id INT NOT NULL,
    estimated_cost DECIMAL(12,2) NULL,
    expected_delivery DATE NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'AUTHORISED',
    received_quantity INT NULL,
    received_by INT NULL,
    received_at TIMESTAMP NULL,
    accepted_by INT NULL,
    accepted_at TIMESTAMP NULL,
    CONSTRAINT fk_procurement_request FOREIGN KEY (request_id) REFERENCES resource_requests(request_id),
    CONSTRAINT fk_procurement_line FOREIGN KEY (line_item_id) REFERENCES resource_request_line_items(line_item_id),
    CONSTRAINT fk_procurement_vendor FOREIGN KEY (vendor_id) REFERENCES vendors(vendor_id),
    CONSTRAINT fk_procurement_receiver FOREIGN KEY (received_by) REFERENCES users(user_id),
    CONSTRAINT fk_procurement_accepter FOREIGN KEY (accepted_by) REFERENCES users(user_id),
    CONSTRAINT chk_procurement_status CHECK (status IN ('AUTHORISED', 'ORDERED', 'RECEIVED', 'ACCEPTED', 'CANCELLED')),
    UNIQUE KEY uq_procurement_line (line_item_id)
);
