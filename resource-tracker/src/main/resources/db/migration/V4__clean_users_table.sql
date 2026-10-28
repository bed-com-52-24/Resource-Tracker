ALTER TABLE users DROP FOREIGN KEY fk_user_department;
ALTER TABLE users DROP FOREIGN KEY fk_user_team;

ALTER TABLE users DROP COLUMN first_name;
ALTER TABLE users DROP COLUMN last_name;
ALTER TABLE users DROP COLUMN department_id;
ALTER TABLE users DROP COLUMN team_id;
ALTER TABLE users DROP COLUMN account_status;
ALTER TABLE users MODIFY COLUMN department VARCHAR(100) NULL;