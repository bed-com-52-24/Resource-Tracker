-- Keep only numeric request references that still point at an existing request.
UPDATE notifications
SET request_id = CASE
    WHEN request_id REGEXP '^[0-9]+$' THEN CAST(request_id AS UNSIGNED)
    ELSE NULL
END
WHERE request_id IS NOT NULL;

UPDATE notifications n
LEFT JOIN resource_requests rr ON rr.request_id = CAST(n.request_id AS UNSIGNED)
SET n.request_id = NULL
WHERE n.request_id IS NOT NULL AND rr.request_id IS NULL;

ALTER TABLE notifications
    DROP FOREIGN KEY fk_notification_recipient;

ALTER TABLE notifications
    MODIFY COLUMN notification_id INT NOT NULL AUTO_INCREMENT,
    MODIFY COLUMN request_id INT NULL;

ALTER TABLE notifications
    ADD CONSTRAINT fk_notification_request
        FOREIGN KEY (request_id) REFERENCES resource_requests(request_id),
    ADD CONSTRAINT fk_notification_recipient
        FOREIGN KEY (recipient_id) REFERENCES users(user_id);
