-- INTERNAL resources may be shared across departments when department_id is NULL.
-- The create API intentionally does not require a department assignment.
ALTER TABLE resources
    DROP CHECK chk_resource_type_rules,
    ADD CONSTRAINT chk_resource_type_rules CHECK (
        (type = 'INTERNAL' AND vendor_id IS NULL AND quantity IS NOT NULL AND quantity >= 0)
        OR (type = 'EXTERNAL' AND department_id IS NULL AND quantity IS NULL)
    );
