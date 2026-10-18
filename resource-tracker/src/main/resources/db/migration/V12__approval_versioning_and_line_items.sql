ALTER TABLE resource_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS request_line_items (
                                                  line_id INT AUTO_INCREMENT,
                                                  request_id INT NOT NULL,
                                                  item_description VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(150) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    batch_number VARCHAR(100) NULL,
    category VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'EXTERNAL',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decision_comment VARCHAR(500) NULL,
    PRIMARY KEY (line_id),
    CONSTRAINT fk_line_request FOREIGN KEY (request_id)
    REFERENCES resource_requests (request_id)
    );