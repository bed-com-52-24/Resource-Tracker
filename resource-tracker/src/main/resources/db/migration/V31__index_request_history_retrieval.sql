CREATE INDEX idx_request_history_actor_time
    ON request_history (actor_id, created_at);

CREATE INDEX idx_request_history_event_time
    ON request_history (event_type, created_at);

CREATE INDEX idx_request_history_line_item_time
    ON request_history (line_item_id, created_at);
