-- Convert legacy UUID project links to the integer projects.project_id key used by the app.
CREATE TABLE team_projects_v59 (
    team_id INT NOT NULL,
    project_id INT NOT NULL,
    PRIMARY KEY (team_id, project_id),
    CONSTRAINT fk_team_projects_v59_team
        FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE,
    CONSTRAINT fk_team_projects_v59_project
        FOREIGN KEY (project_id) REFERENCES projects(project_id)
);

INSERT INTO team_projects_v59 (team_id, project_id)
SELECT tp.team_id, p.project_id
FROM team_projects tp
JOIN projects p ON p.id = tp.project_id;

DROP TABLE team_projects;
RENAME TABLE team_projects_v59 TO team_projects;

ALTER TABLE resource_requests
    DROP FOREIGN KEY fk_request_project;

ALTER TABLE resource_requests
    ADD CONSTRAINT fk_request_project
        FOREIGN KEY (project_id) REFERENCES projects(project_id);

-- Remove the legacy UUID project key after all dependent links use project_id.
ALTER TABLE projects
    DROP INDEX uq_projects_id,
    DROP COLUMN id;
