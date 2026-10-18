ALTER TABLE teams
    ADD COLUMN manager_id INT NULL,
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    ADD CONSTRAINT fk_team_manager
        FOREIGN KEY (manager_id) REFERENCES users(user_id);

CREATE TABLE IF NOT EXISTS team_projects (
    team_id INT NOT NULL,
    project_id CHAR(36) NOT NULL,

    PRIMARY KEY (team_id, project_id),

    CONSTRAINT fk_team_projects_team
        FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE,
    CONSTRAINT fk_team_projects_project
        FOREIGN KEY (project_id) REFERENCES projects(id)
);

INSERT INTO team_projects (team_id, project_id)
SELECT t.team_id, p.id
FROM teams t
JOIN projects p ON p.project_id = t.project_id
WHERE t.project_id IS NOT NULL;
