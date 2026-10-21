ALTER TABLE inventory_audit_logs
    DROP FOREIGN KEY fk_inventory_audit_inventory;

ALTER TABLE resource_inventory
    DROP FOREIGN KEY fk_inventory_resource;

ALTER TABLE resources
    ADD COLUMN quantity INT NULL,
    ADD COLUMN location VARCHAR(120) NULL,
    ADD COLUMN reorder_level INT NOT NULL DEFAULT 0,
    ADD COLUMN status VARCHAR(20) NULL;

UPDATE resources r
LEFT JOIN (
    SELECT resource_id,
           SUM(total_quantity) AS total_quantity,
           MIN(id) AS first_inventory_id
    FROM resource_inventory
    GROUP BY resource_id
) i ON i.resource_id = r.id
LEFT JOIN resource_inventory first_inventory
    ON first_inventory.id = i.first_inventory_id
SET r.quantity = CASE
        WHEN r.type = 'INTERNAL' THEN COALESCE(i.total_quantity, 0)
        ELSE NULL
    END,
    r.location = first_inventory.location,
    r.reorder_level = COALESCE(first_inventory.reorder_level, 0),
    r.status = CASE
        WHEN r.type = 'INTERNAL' AND COALESCE(i.total_quantity, 0) > 0 THEN 'AVAILABLE'
        WHEN r.type = 'INTERNAL' THEN 'OUT_OF_STOCK'
        ELSE NULL
    END;

ALTER TABLE resources
    DROP CHECK chk_resource_type_rules,
    ADD CONSTRAINT chk_resource_type_rules CHECK (
        (type = 'INTERNAL' AND department_id IS NOT NULL AND vendor_id IS NULL AND quantity IS NOT NULL AND quantity >= 0)
        OR (type = 'EXTERNAL' AND department_id IS NULL AND quantity IS NULL)
    ),
    ADD CONSTRAINT chk_resource_status CHECK (
        (type = 'INTERNAL' AND status IN ('AVAILABLE', 'OUT_OF_STOCK'))
        OR (type = 'EXTERNAL' AND status IS NULL)
    ),
    ADD CONSTRAINT chk_resource_reorder_level CHECK (reorder_level >= 0);

DROP TABLE inventory_audit_logs;
DROP TABLE resource_inventory;
