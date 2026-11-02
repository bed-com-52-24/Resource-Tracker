CREATE TABLE department_budgets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    department_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    allocated_amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at DATETIME NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME NOT NULL,
    updated_by BIGINT NOT NULL,

    CONSTRAINT fk_budget_department
        FOREIGN KEY (department_id)
        REFERENCES departments(id),

    CONSTRAINT chk_budget_amount
        CHECK (allocated_amount >= 0),

    CONSTRAINT chk_budget_period
        CHECK (period_start <= period_end),

    CONSTRAINT uq_department_budget_period
        UNIQUE (department_id, period_start, period_end)
) ENGINE=InnoDB;


CREATE TABLE department_budget_audits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    budget_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    previous_amount DECIMAL(19,2),
    new_amount DECIMAL(19,2) NOT NULL,
    reason TEXT,
    changed_by BIGINT NOT NULL,
    changed_at DATETIME NOT NULL,

    CONSTRAINT fk_budget_audit
        FOREIGN KEY (budget_id)
        REFERENCES department_budgets(id),

    CONSTRAINT chk_budget_audit_action
        CHECK (action IN ('CREATED', 'UPDATED', 'OVERRIDE'))
) ENGINE=InnoDB;


CREATE INDEX idx_budget_department
ON department_budgets(department_id);

CREATE INDEX idx_budget_period
ON department_budgets(period_start, period_end);

CREATE INDEX idx_budget_audit_budget
ON department_budget_audits(budget_id);