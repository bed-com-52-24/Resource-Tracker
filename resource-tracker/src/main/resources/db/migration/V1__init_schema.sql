CREATE DATABASE IF NOT EXISTS resource_tracker;

USE resource_tracker;

CREATE TABLE IF NOT EXISTS departments (
    department_id INT AUTO_INCREMENT,
    department_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(20) DEFAULT 'ACTIVE',

    PRIMARY KEY (department_id),
    UNIQUE (department_name)
);



CREATE TABLE IF NOT EXISTS projects (
    project_id INT AUTO_INCREMENT,
    project_name VARCHAR(150) NOT NULL,
    department_id INT NOT NULL,
    description VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',


    PRIMARY KEY (project_id),
    UNIQUE (department_id, project_name),

    CONSTRAINT fk_project_department
        FOREIGN KEY (department_id)
        REFERENCES departments(department_id)
);


CREATE TABLE IF NOT EXISTS teams (
    team_id INT AUTO_INCREMENT,
    team_name VARCHAR(100) NOT NULL,
    department_id INT NOT NULL,
    project_id INT,
    status VARCHAR(20) DEFAULT 'ACTIVE',

    PRIMARY KEY (team_id),
    UNIQUE (department_id, team_name),

    CONSTRAINT fk_team_department
        FOREIGN KEY (department_id)
        REFERENCES departments(department_id),

    CONSTRAINT fk_team_project
        FOREIGN KEY (project_id)
        REFERENCES projects(project_id)
);


CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    department_id INT NOT NULL,
    team_id INT,
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    PRIMARY KEY (user_id),
    UNIQUE (email),

    CONSTRAINT fk_user_department
        FOREIGN KEY (department_id)
        REFERENCES departments(department_id),

    CONSTRAINT fk_user_team
        FOREIGN KEY (team_id)
        REFERENCES teams(team_id)
);



CREATE TABLE IF NOT EXISTS vendors (
    vendor_id INT AUTO_INCREMENT,
    vendor_name VARCHAR(150) NOT NULL,
    contact_information VARCHAR(255),
    status VARCHAR(20) DEFAULT 'ACTIVE',

    PRIMARY KEY (vendor_id)
);




CREATE TABLE IF NOT EXISTS routing_rules (
    routing_rule_id INT AUTO_INCREMENT,
    department_id INT,
    project_id INT,
    team_id INT,
    line_manager_id INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,

    PRIMARY KEY (routing_rule_id),

    CONSTRAINT fk_routing_department
        FOREIGN KEY (department_id)
        REFERENCES departments(department_id),

    CONSTRAINT fk_routing_project
        FOREIGN KEY (project_id)
        REFERENCES projects(project_id),

    CONSTRAINT fk_routing_team
        FOREIGN KEY (team_id)
        REFERENCES teams(team_id),

    CONSTRAINT fk_routing_manager
        FOREIGN KEY (line_manager_id)
        REFERENCES users(user_id)
);


CREATE TABLE IF NOT EXISTS resource_requests (
    request_id INT AUTO_INCREMENT,
    item_description VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(150) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    batch_number VARCHAR(100),

    vendor_id INT NOT NULL,
    department_id INT NOT NULL,
    project_id INT NOT NULL,
    team_id INT NOT NULL,
    requester_id INT NOT NULL,
    assigned_manager_id INT NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (request_id),

    CONSTRAINT fk_request_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES vendors(vendor_id),

    CONSTRAINT fk_request_department
        FOREIGN KEY (department_id)
        REFERENCES departments(department_id),

    CONSTRAINT fk_request_project
        FOREIGN KEY (project_id)
        REFERENCES projects(project_id),

    CONSTRAINT fk_request_team
        FOREIGN KEY (team_id)
        REFERENCES teams(team_id),

    CONSTRAINT fk_request_requester
        FOREIGN KEY (requester_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_request_manager
        FOREIGN KEY (assigned_manager_id)
        REFERENCES users(user_id),

    CONSTRAINT chk_request_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_request_price
        CHECK (unit_price >= 0),

    CONSTRAINT chk_request_status
        CHECK (
            status IN (
                'PENDING',
                'QUERIED',
                'APPROVED',
                'REJECTED'
            )
        )
);


CREATE TABLE IF NOT EXISTS audit_logs (
    audit_id INT AUTO_INCREMENT,
    request_id INT NOT NULL,
    actor_id INT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    previous_state VARCHAR(20),
    new_state VARCHAR(20) NOT NULL,
    action VARCHAR(50) NOT NULL,
    comment VARCHAR(500),

    PRIMARY KEY (audit_id),

    CONSTRAINT fk_audit_request
        FOREIGN KEY (request_id)
        REFERENCES resource_requests(request_id),

    CONSTRAINT fk_audit_actor
        FOREIGN KEY (actor_id)
        REFERENCES users(user_id)
);




CREATE TABLE IF NOT EXISTS notifications (
    notification_id INT AUTO_INCREMENT,
    user_id INT NOT NULL,
    request_id INT,
    message VARCHAR(500) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (notification_id),

    CONSTRAINT fk_notification_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_notification_request
        FOREIGN KEY (request_id)
        REFERENCES resource_requests(request_id)
);
