CREATE TABLE team_member_history (
    history_id   INT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT NOT NULL,
    from_team_id INT NULL,
    to_team_id   INT NULL,
    action       VARCHAR(20) NOT NULL,
    performed_by INT NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_member_history_user
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_member_history_from_team
        FOREIGN KEY (from_team_id) REFERENCES teams(team_id),
    CONSTRAINT fk_member_history_to_team
        FOREIGN KEY (to_team_id) REFERENCES teams(team_id),
    CONSTRAINT fk_member_history_performed_by
        FOREIGN KEY (performed_by) REFERENCES users(user_id),
    INDEX idx_member_history_user (user_id, created_at)
);
