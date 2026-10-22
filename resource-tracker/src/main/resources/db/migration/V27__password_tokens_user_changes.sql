ALTER TABLE users
    MODIFY COLUMN password_hash VARCHAR(255) NULL,
    MODIFY COLUMN full_name VARCHAR(255) NULL;

CREATE TABLE password_tokens (
    token_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(10) NOT NULL,
    expires_at TIMESTAMP NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT chk_password_tokens_purpose
        CHECK (purpose IN ('SETUP', 'RESET')),
    INDEX idx_password_tokens_user (user_id, purpose)
);
