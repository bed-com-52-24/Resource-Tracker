INSERT INTO departments (department_name)
SELECT DISTINCT u.department
FROM users u
LEFT JOIN departments d ON LOWER(d.department_name) = LOWER(u.department)
WHERE u.department IS NOT NULL
  AND u.department <> ''
  AND d.department_id IS NULL;

INSERT INTO teams (team_name, department_id)
SELECT DISTINCT ut.team, d.department_id
FROM user_teams ut
JOIN users u ON u.user_id = ut.user_id
JOIN departments d ON LOWER(d.department_name) = LOWER(u.department)
LEFT JOIN teams t
    ON LOWER(t.team_name) = LOWER(ut.team)
   AND t.department_id = d.department_id
WHERE ut.team IS NOT NULL
  AND ut.team <> ''
  AND t.team_id IS NULL;

ALTER TABLE users ADD COLUMN department_id_new INT NULL;

UPDATE users u
JOIN departments d ON LOWER(d.department_name) = LOWER(u.department)
SET u.department_id_new = d.department_id;

ALTER TABLE user_teams ADD COLUMN team_id_new INT NULL;

UPDATE user_teams ut
JOIN users u ON u.user_id = ut.user_id
JOIN departments d ON LOWER(d.department_name) = LOWER(u.department)
JOIN teams t
    ON LOWER(t.team_name) = LOWER(ut.team)
   AND t.department_id = d.department_id
SET ut.team_id_new = t.team_id;

ALTER TABLE user_teams
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (user_id, team_id_new);

ALTER TABLE users
    DROP COLUMN department,
    CHANGE COLUMN department_id_new department_id INT NOT NULL,
    ADD CONSTRAINT fk_user_department_new
        FOREIGN KEY (department_id) REFERENCES departments(department_id);

ALTER TABLE user_teams
    DROP COLUMN team,
    CHANGE COLUMN team_id_new team_id INT NOT NULL,
    ADD CONSTRAINT fk_user_teams_team
        FOREIGN KEY (team_id) REFERENCES teams(team_id)
        ON DELETE CASCADE;
