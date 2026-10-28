ALTER TABLE request_history
    MODIFY COLUMN actor_id INT NULL,
    ADD COLUMN actor_type VARCHAR(10) NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT chk_history_actor CHECK (
        (actor_type = 'USER' AND actor_id IS NOT NULL) OR (actor_type = 'SYSTEM' AND actor_id IS NULL));

ALTER TABLE stock_reservations
    MODIFY COLUMN created_by INT NULL,
    ADD COLUMN actor_type VARCHAR(10) NOT NULL DEFAULT 'USER',
    ADD COLUMN waitlist_entry_id INT NULL,
    ADD CONSTRAINT fk_reservation_waitlist_entry
        FOREIGN KEY (waitlist_entry_id) REFERENCES waitlist_entries(waitlist_entry_id),
    ADD CONSTRAINT chk_reservation_actor CHECK (
        (actor_type = 'USER' AND created_by IS NOT NULL) OR (actor_type = 'SYSTEM' AND created_by IS NULL));

CREATE INDEX idx_reservation_waitlist_entry ON stock_reservations(waitlist_entry_id);
