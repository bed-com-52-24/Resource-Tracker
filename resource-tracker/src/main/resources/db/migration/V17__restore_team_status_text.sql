-- Team status is represented as ACTIVE / INACTIVE throughout the application.
-- Recreate the column as text because V16 changed it to a boolean.
ALTER TABLE teams
    ADD COLUMN status_text VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

UPDATE teams
SET status_text = CASE
    WHEN status = TRUE THEN 'ACTIVE'
    ELSE 'INACTIVE'
END;

ALTER TABLE teams
    DROP COLUMN status,
    CHANGE COLUMN status_text status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
