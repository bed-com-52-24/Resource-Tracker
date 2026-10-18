ALTER TABLE notifications
    DROP FOREIGN KEY fk_notification_request,
    DROP FOREIGN KEY fk_notification_user,
    CHANGE COLUMN notification_id notification_id BIGINT NOT NULL AUTO_INCREMENT,
    CHANGE COLUMN user_id recipient_id INT NOT NULL,
    CHANGE COLUMN request_id request_id VARCHAR(64) NULL,
    CHANGE COLUMN message message TEXT NOT NULL,
    CHANGE COLUMN notification_type type VARCHAR(40) NOT NULL,
    CHANGE COLUMN is_read read_flag BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE notifications
    ADD CONSTRAINT fk_notification_recipient
        FOREIGN KEY (recipient_id)
        REFERENCES users(user_id);
