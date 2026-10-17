CREATE TABLE resource_inventory (
    id BIGINT AUTO_INCREMENT NOT NULL,
    resource_id BINARY(16) NOT NULL,
    location VARCHAR(120) NOT NULL,
    total_quantity INT NOT NULL,
    reserved_quantity INT NOT NULL DEFAULT 0,
    issued_quantity INT NOT NULL DEFAULT 0,
    reorder_level INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT uq_resource_inventory_resource_location UNIQUE (resource_id, location),
    CONSTRAINT fk_inventory_resource FOREIGN KEY (resource_id) REFERENCES resources(id),
    CONSTRAINT chk_inventory_total CHECK (total_quantity >= 0),
    CONSTRAINT chk_inventory_reserved CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_inventory_issued CHECK (issued_quantity >= 0),
    CONSTRAINT chk_inventory_reorder CHECK (reorder_level >= 0),
    CONSTRAINT chk_inventory_not_overallocated CHECK (
        reserved_quantity + issued_quantity <= total_quantity
    )
);

CREATE INDEX idx_inventory_resource ON resource_inventory(resource_id);
CREATE INDEX idx_inventory_low_stock
    ON resource_inventory(total_quantity, reserved_quantity, issued_quantity, reorder_level);

CREATE TABLE inventory_audit_logs (
    id BIGINT AUTO_INCREMENT NOT NULL,
    inventory_id BIGINT NOT NULL,
    actor_id INT NULL,
    action VARCHAR(40) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    previous_total_quantity INT NOT NULL,
    previous_reserved_quantity INT NOT NULL,
    previous_issued_quantity INT NOT NULL,
    new_total_quantity INT NOT NULL,
    new_reserved_quantity INT NOT NULL,
    new_issued_quantity INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_inventory_audit_inventory FOREIGN KEY (inventory_id)
        REFERENCES resource_inventory(id),
    CONSTRAINT fk_inventory_audit_actor FOREIGN KEY (actor_id)
        REFERENCES users(user_id)
);
