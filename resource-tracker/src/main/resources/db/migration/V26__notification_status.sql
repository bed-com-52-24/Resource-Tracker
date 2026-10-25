ALTER TABLE notifications
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'UNREAD',
    ADD COLUMN read_at TIMESTAMP NULL;

UPDATE notifications
SET status = 'READ',
    read_at = created_at
WHERE read_flag = TRUE;

ALTER TABLE notifications
    DROP COLUMN read_flag,
    ADD INDEX idx_notifications_recipient_status (recipient_id, status, created_at);
