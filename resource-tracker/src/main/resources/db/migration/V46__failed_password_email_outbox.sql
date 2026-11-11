CREATE TABLE failed_password_emails (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(200) NOT NULL,
    username VARCHAR(100) NOT NULL,
    encrypted_code VARCHAR(512) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    INDEX idx_failed_password_email_due (next_attempt_at, attempts, created_at)
);
