-- Convert existing values
UPDATE teams
SET status = CASE
    WHEN status IS NULL OR UPPER(status) = 'ACTIVE' THEN '1'
    ELSE '0'
END;

-- updating the table
ALTER TABLE teams
    MODIFY COLUMN status BOOLEAN NOT NULL DEFAULT TRUE;