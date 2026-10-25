CREATE TABLE resources (
    id BINARY(16) NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(120) NOT NULL,
    type ENUM('INTERNAL', 'EXTERNAL') NOT NULL,
    category ENUM('CONSUMABLE', 'EQUIPMENT', 'SOFTWARE', 'OTHER') NOT NULL,
    description VARCHAR(500),
    unit_of_measure VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    department_id INT NULL,
    vendor_id INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uq_resources_code UNIQUE (code),

    INDEX idx_resources_name (name),
    INDEX idx_resources_department (department_id),
    INDEX idx_resources_vendor (vendor_id),
    INDEX idx_resources_type_active (type, is_active),
    FULLTEXT INDEX ft_resources_name_description (name, description),

    CONSTRAINT fk_resource_department
        FOREIGN KEY (department_id)
        REFERENCES departments(department_id),
    CONSTRAINT fk_resource_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES vendors(vendor_id),
    CONSTRAINT chk_resource_type_rules CHECK (
        (type = 'INTERNAL' AND department_id IS NOT NULL AND vendor_id IS NULL)
        OR (type = 'EXTERNAL' AND department_id IS NULL)
    )
);

CREATE TABLE code_sequence (
    name VARCHAR(30) NOT NULL,
    next_value BIGINT NOT NULL DEFAULT 1,

    PRIMARY KEY (name)
);

INSERT INTO code_sequence (name, next_value)
VALUES ('RESOURCE', 1);
