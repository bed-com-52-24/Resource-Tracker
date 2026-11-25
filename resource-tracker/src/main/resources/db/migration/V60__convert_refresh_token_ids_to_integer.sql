-- Invalidate legacy refresh tokens before replacing their UUID primary key.
DELETE FROM refresh_tokens;

ALTER TABLE refresh_tokens
    ADD COLUMN refresh_token_id INT NOT NULL AUTO_INCREMENT UNIQUE FIRST;

ALTER TABLE refresh_tokens
    DROP PRIMARY KEY,
    DROP COLUMN id,
    CHANGE COLUMN refresh_token_id id INT NOT NULL AUTO_INCREMENT,
    ADD PRIMARY KEY (id);
