CREATE TABLE IF NOT EXISTS department_budget (
    department_id INT NOT NULL,
    budget_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    committed_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    PRIMARY KEY (department_id),
    CONSTRAINT fk_budget_department FOREIGN KEY (department_id) REFERENCES departments (department_id),
    CONSTRAINT chk_budget_committed CHECK (committed_amount >= 0)
);
