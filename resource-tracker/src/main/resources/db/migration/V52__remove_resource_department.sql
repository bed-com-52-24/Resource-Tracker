-- Resources are shared inventory/catalog items; department ownership remains on requests.
ALTER TABLE resources
    DROP CHECK chk_resource_type_rules,
    DROP FOREIGN KEY fk_resource_department,
    DROP INDEX idx_resources_availability_filters,
    DROP INDEX idx_resources_department,
    DROP COLUMN department_id,
    ADD CONSTRAINT chk_resource_type_rules CHECK (
        (type = 'INTERNAL' AND vendor_id IS NULL AND quantity IS NOT NULL AND quantity >= 0)
        OR (type = 'EXTERNAL' AND quantity IS NULL)
    );
