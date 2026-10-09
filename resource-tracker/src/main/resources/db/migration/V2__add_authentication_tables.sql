CREATE TABLE refresh_tokens (
    id BINARY(16) PRIMARY KEY,
    user_id INT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user
    ON refresh_tokens(user_id);

CREATE TABLE revoked_access_tokens (
    jti VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMP NOT NULL
);
