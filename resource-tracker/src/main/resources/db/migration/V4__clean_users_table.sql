ALTER TABLE users DROP FOREIGN KEY fk_user_department;
ALTER TABLE users DROP FOREIGN KEY fk_user_team;

ALTER TABLE users
    DROP COLUMN first_name,
    DROP COLUMN last_name,
    DROP COLUMN department_id,
    DROP COLUMN team_id,
    DROP COLUMN account_status,
    MODIFY COLUMN department VARCHAR(100) NULL;