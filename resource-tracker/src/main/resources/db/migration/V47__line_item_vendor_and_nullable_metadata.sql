ALTER TABLE resource_requests
    MODIFY COLUMN manufacturer VARCHAR(150) NULL,
    MODIFY COLUMN vendor_id INT NULL;

ALTER TABLE resource_request_line_items
    ADD COLUMN vendor_id INT NULL AFTER fulfillment_preference,
    MODIFY COLUMN manufacturer VARCHAR(120) NULL;

UPDATE resource_request_line_items li
JOIN resource_requests rr ON rr.request_id = li.request_id
SET li.vendor_id = rr.vendor_id
WHERE li.vendor_id IS NULL
  AND rr.vendor_id IS NOT NULL;

UPDATE resource_requests
SET vendor_id = NULL
WHERE vendor_id IS NOT NULL;
