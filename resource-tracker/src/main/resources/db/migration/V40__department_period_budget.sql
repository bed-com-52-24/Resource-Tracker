CREATE TABLE IF NOT EXISTS budget (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    department_id INT NOT NULL,
    period_type VARCHAR(16) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    committed_amount DECIMAL(19,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_period_budget_department FOREIGN KEY (department_id) REFERENCES departments (department_id),
    CONSTRAINT uk_budget_department_period UNIQUE (department_id, period_type, period_start, period_end)
);

CREATE INDEX idx_budget_department ON budget (department_id);
CREATE INDEX idx_budget_period ON budget (period_start, period_end);

CREATE TABLE IF NOT EXISTS budget_commitment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    budget_id BIGINT NOT NULL,
    request_id INT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    comment VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_budget_commitment_budget FOREIGN KEY (budget_id) REFERENCES budget (id)
);

CREATE INDEX idx_budget_commitment_budget ON budget_commitment (budget_id);
