-- 1. Waitlist table. FIFO order = created_at, then waitlist_entry_id as tie-breaker.
CREATE TABLE waitlist_entries (
    waitlist_entry_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    request_id        INT NOT NULL,
    line_item_id      INT NOT NULL,
    resource_id       INT NOT NULL,
    quantity          INT NOT NULL,
    reserved_quantity INT NOT NULL DEFAULT 0,
    issued_quantity   INT NOT NULL DEFAULT 0,
    status            VARCHAR(12) NOT NULL DEFAULT 'WAITING',
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_waitlist_request  FOREIGN KEY (request_id)   REFERENCES resource_requests (request_id),
    CONSTRAINT fk_waitlist_line     FOREIGN KEY (line_item_id) REFERENCES resource_request_line_items (line_item_id),
    CONSTRAINT fk_waitlist_resource FOREIGN KEY (resource_id)  REFERENCES resources (id),
    CONSTRAINT chk_waitlist_status
        CHECK (status IN ('WAITING', 'FULFILLABLE', 'FULFILLED', 'CANCELLED')),
    CONSTRAINT chk_waitlist_quantities
        CHECK (quantity > 0
           AND reserved_quantity >= 0
           AND issued_quantity >= 0
           AND issued_quantity <= reserved_quantity
           AND reserved_quantity <= quantity)
);

-- Ordered FIFO lookup and row locking per resource.
CREATE INDEX idx_waitlist_fifo    ON waitlist_entries (resource_id, status, created_at, waitlist_entry_id);
CREATE INDEX idx_waitlist_request ON waitlist_entries (request_id);
CREATE INDEX idx_waitlist_line    ON waitlist_entries (line_item_id);

-- 2. Explicit SYSTEM actor: USER rows keep a user id, SYSTEM rows have none (no fake user).
ALTER TABLE stock_reservations
    MODIFY created_by INT NULL,
    ADD COLUMN actor_type VARCHAR(10) NOT NULL DEFAULT 'USER',
    ADD COLUMN waitlist_entry_id INT NULL,
    ADD CONSTRAINT fk_reservation_waitlist FOREIGN KEY (waitlist_entry_id) REFERENCES waitlist_entries (waitlist_entry_id),
    ADD CONSTRAINT chk_reservation_actor CHECK (
        (actor_type = 'USER' AND created_by IS NOT NULL) OR (actor_type = 'SYSTEM' AND created_by IS NULL));

ALTER TABLE request_history
    MODIFY actor_id INT NULL,
    ADD COLUMN actor_type VARCHAR(10) NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT chk_history_actor CHECK (
        (actor_type = 'USER' AND actor_id IS NOT NULL) OR (actor_type = 'SYSTEM' AND actor_id IS NULL));

-- 3. Allow the WAITLISTED request status (statuses after V29: PENDING, QUERIED, APPROVED, REJECTED, CANCELLED).
ALTER TABLE resource_requests DROP CHECK chk_request_status;
ALTER TABLE resource_requests ADD CONSTRAINT chk_request_status CHECK (status IN (
    'PENDING', 'QUERIED', 'APPROVED', 'REJECTED', 'CANCELLED', 'WAITLISTED'));