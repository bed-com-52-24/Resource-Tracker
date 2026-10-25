ALTER TABLE resource_requests
    ADD COLUMN request_number VARCHAR(20) NULL,
    ADD COLUMN justification VARCHAR(500) NULL,
    ADD COLUMN total_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN edited_after_query BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN queried_at TIMESTAMP NULL;

-- 2. Backfill permanent-looking identifiers for existing requests.
-- These identifiers preserve existing rows; new requests will use
-- a dedicated sequence allocator in the service.
UPDATE resource_requests
SET request_number = CONCAT(
    'REQ-',
    YEAR(created_at),
    '-',
    LPAD(request_id, 6, '0')
)
WHERE request_number IS NULL;

ALTER TABLE resource_requests
    MODIFY COLUMN request_number VARCHAR(20) NOT NULL,
    ADD CONSTRAINT uq_resource_requests_request_number
        UNIQUE (request_number);

-- 3. Make room for the required lifecycle statuses.
ALTER TABLE resource_requests
    DROP CHECK chk_request_status;

ALTER TABLE resource_requests
    ADD CONSTRAINT chk_request_status
    CHECK (
        status IN (
            'PENDING',
            'QUERIED',
            'APPROVED',
            'REJECTED',
            'CANCELLED'
        )
    );

-- 4. Create a table for multiple line items per request.
CREATE TABLE resource_request_line_items (
    line_item_id INT AUTO_INCREMENT PRIMARY KEY,
    request_id INT NOT NULL,
    description VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(120) NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    quantity INT NOT NULL,
    category VARCHAR(20) NOT NULL,
    batch_number VARCHAR(50) NULL,

    CONSTRAINT fk_line_item_request
        FOREIGN KEY (request_id)
        REFERENCES resource_requests(request_id)
        ON DELETE CASCADE,

    CONSTRAINT chk_line_item_price
        CHECK (unit_price > 0),

    CONSTRAINT chk_line_item_quantity
        CHECK (quantity BETWEEN 1 AND 100000),

    CONSTRAINT chk_line_item_category
        CHECK (
            category IN (
                'CONSUMABLE',
                'EQUIPMENT',
                'SOFTWARE',
                'OTHER'
            )
        ),

    CONSTRAINT chk_line_item_batch
        CHECK (
            (category = 'CONSUMABLE' AND batch_number IS NOT NULL)
            OR
            (category <> 'CONSUMABLE' AND batch_number IS NULL)
        )
);

-- 5. Preserve each existing request's item as its first line item.
-- Existing rows do not have a category column, so classify them as OTHER.
INSERT INTO resource_request_line_items (
    request_id,
    description,
    manufacturer,
    unit_price,
    quantity,
    category,
    batch_number
)
SELECT
    request_id,
    item_description,
    manufacturer,
    unit_price,
    quantity,
    'OTHER',
    NULL
FROM resource_requests;

-- 6. Initialize totals from the existing single-item requests.
UPDATE resource_requests
SET total_amount = (
    SELECT COALESCE(SUM(li.unit_price * li.quantity), 0.00)
    FROM resource_request_line_items li
    WHERE li.request_id = resource_requests.request_id
);

-- 7. Store idempotency keys separately.
CREATE TABLE resource_request_idempotency (
    idempotency_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(64) NOT NULL,
    requester_id INT NOT NULL,
    request_id INT NULL,
    processing BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_request_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT fk_idempotency_requester
        FOREIGN KEY (requester_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_idempotency_request
        FOREIGN KEY (request_id)
        REFERENCES resource_requests(request_id)
);

-- 8. Create a dedicated yearly sequence table.
CREATE TABLE resource_request_sequences (
    request_year INT PRIMARY KEY,
    last_sequence INT NOT NULL DEFAULT 0
);

-- 9. Add a queried timestamp for requests already queried, if any.
UPDATE resource_requests
SET queried_at = updated_at
WHERE status = 'QUERIED';