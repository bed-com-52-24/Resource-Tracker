ALTER TABLE resources
    ADD COLUMN tracking_method VARCHAR(20) NOT NULL DEFAULT 'QUANTITY',
    DROP CHECK chk_resource_type_rules,
    ADD CONSTRAINT chk_resources_tracking_method CHECK (tracking_method IN ('QUANTITY','INDIVIDUAL')),
    ADD CONSTRAINT chk_resource_type_rules CHECK (
        (type = 'INTERNAL' AND vendor_id IS NULL AND tracking_method = 'QUANTITY' AND quantity IS NOT NULL AND quantity >= 0)
        OR (type = 'INTERNAL' AND vendor_id IS NULL AND tracking_method = 'INDIVIDUAL' AND quantity IS NULL)
        OR (type = 'EXTERNAL' AND tracking_method = 'QUANTITY' AND quantity IS NULL)
    );

CREATE TABLE resource_assets (
    asset_id INT NOT NULL AUTO_INCREMENT,
    resource_id INT NOT NULL,
    asset_code VARCHAR(80) NOT NULL,
    serial_number VARCHAR(120) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    location VARCHAR(120) NULL,
    assigned_user_id INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (asset_id),
    CONSTRAINT uq_resource_assets_code UNIQUE (asset_code),
    CONSTRAINT uq_resource_assets_serial UNIQUE (serial_number),
    CONSTRAINT fk_resource_assets_resource FOREIGN KEY (resource_id) REFERENCES resources(id),
    CONSTRAINT fk_resource_assets_user FOREIGN KEY (assigned_user_id) REFERENCES users(user_id),
    CONSTRAINT chk_resource_assets_status CHECK (status IN ('AVAILABLE','RESERVED','ISSUED','DAMAGED','MISSING','DISPOSED')),
    INDEX idx_resource_assets_resource_status (resource_id, status)
);

CREATE TABLE asset_movements (
    movement_id INT NOT NULL AUTO_INCREMENT,
    asset_id INT NOT NULL,
    movement_type VARCHAR(20) NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    previous_location VARCHAR(120) NULL,
    new_location VARCHAR(120) NULL,
    reason VARCHAR(500) NOT NULL,
    actor_id INT NOT NULL,
    related_request_id INT NULL,
    related_issue_id INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (movement_id),
    CONSTRAINT fk_asset_movements_asset FOREIGN KEY (asset_id) REFERENCES resource_assets(asset_id),
    CONSTRAINT fk_asset_movements_actor FOREIGN KEY (actor_id) REFERENCES users(user_id),
    INDEX idx_asset_movements_asset_time (asset_id, created_at)
);
