ALTER TABLE waitlist_entries
    DROP CHECK chk_waitlist_status,
    DROP CHECK chk_waitlist_quantities,
    ADD CONSTRAINT chk_waitlist_status CHECK (
        status IN ('WAITING', 'FULFILLABLE', 'FULFILLED', 'CANCELLED', 'WAIVED')
    ),
    ADD CONSTRAINT chk_waitlist_quantities CHECK (
        quantity >= 0
        AND reserved_quantity >= 0
        AND issued_quantity >= 0
        AND issued_quantity <= reserved_quantity
        AND reserved_quantity <= quantity
    );
