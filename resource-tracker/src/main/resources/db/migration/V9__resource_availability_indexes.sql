CREATE INDEX idx_resources_availability_filters
    ON resources(type, is_active, department_id, category);
