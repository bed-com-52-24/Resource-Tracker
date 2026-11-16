ALTER TABLE procurements
    ADD INDEX idx_procurement_line_item (line_item_id),
    ADD COLUMN authorized_quantity INT NULL AFTER line_item_id;

-- The foreign key needs an index on line_item_id. Add a non-unique replacement
-- before removing the unique index so MySQL can keep fk_procurement_line.
ALTER TABLE procurements
    DROP INDEX uq_procurement_line;

UPDATE procurements p
JOIN resource_request_line_items line ON line.line_item_id = p.line_item_id
SET p.authorized_quantity = CASE
    WHEN p.estimated_cost IS NOT NULL AND line.unit_price > 0
        THEN GREATEST(1, ROUND(p.estimated_cost / line.unit_price))
    ELSE COALESCE(line.approved_quantity, line.quantity)
END;

ALTER TABLE procurements
    MODIFY COLUMN authorized_quantity INT NOT NULL,
    ADD CONSTRAINT chk_procurement_authorized_quantity CHECK (authorized_quantity > 0);
