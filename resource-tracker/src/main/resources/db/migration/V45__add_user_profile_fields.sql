ALTER TABLE users
    ADD COLUMN first_name VARCHAR(100) NULL,
    ADD COLUMN last_name VARCHAR(100) NULL,
    ADD COLUMN middle_name VARCHAR(100) NULL,
    ADD COLUMN position VARCHAR(150) NULL,
    ADD COLUMN phone VARCHAR(30) NULL,
    ADD COLUMN address VARCHAR(500) NULL,
    ADD COLUMN title VARCHAR(50) NULL;

UPDATE users
SET first_name = COALESCE(NULLIF(SUBSTRING_INDEX(COALESCE(full_name, username), ' ', 1), ''), username),
    last_name = COALESCE(NULLIF(TRIM(SUBSTRING(COALESCE(full_name, username), LENGTH(SUBSTRING_INDEX(COALESCE(full_name, username), ' ', 1)) + 1)), ''), username)
WHERE first_name IS NULL OR last_name IS NULL;

ALTER TABLE users
    MODIFY COLUMN first_name VARCHAR(100) NOT NULL,
    MODIFY COLUMN last_name VARCHAR(100) NOT NULL;
