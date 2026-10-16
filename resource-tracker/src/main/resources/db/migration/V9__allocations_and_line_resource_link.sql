-- Links an internal line item to the inventory item it asks for
ALTER TABLE request_line_items ADD COLUMN resource_id INT NULL;

-- One row per reservation made when an internal line is approved (SRS: entity defined in #39)
CREATE TABLE IF NOT EXISTS resource_allocation (
                                                   allocation_id INT AUTO_INCREMENT,
                                                   request_id INT NOT NULL,
                                                   line_id INT NOT NULL,
                                                   resource_id INT NOT NULL,
                                                   quantity INT NOT NULL,
                                                   status VARCHAR(20) NOT NULL DEFAULT 'RESERVED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (allocation_id),
    CONSTRAINT fk_allocation_line FOREIGN KEY (line_id)
    REFERENCES request_line_items (line_id)
    );