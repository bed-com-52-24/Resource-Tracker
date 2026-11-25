ALTER TABLE resource_assets
    DROP CHECK chk_resource_assets_status,
    ADD CONSTRAINT chk_resource_assets_status CHECK (status IN ('AVAILABLE','RESERVED','ISSUED','RETURN_PENDING','DAMAGED','MISSING','DISPOSED'));

ALTER TABLE asset_movements
    ADD COLUMN related_return_id INT NULL,
    ADD COLUMN previous_assigned_user_id INT NULL,
    ADD COLUMN new_assigned_user_id INT NULL,
    ADD CONSTRAINT fk_asset_movements_issue FOREIGN KEY (related_issue_id) REFERENCES stock_issues(issue_id),
    ADD CONSTRAINT fk_asset_movements_return FOREIGN KEY (related_return_id) REFERENCES stock_returns(return_id),
    ADD CONSTRAINT fk_asset_movements_previous_user FOREIGN KEY (previous_assigned_user_id) REFERENCES users(user_id),
    ADD CONSTRAINT fk_asset_movements_new_user FOREIGN KEY (new_assigned_user_id) REFERENCES users(user_id);

CREATE TABLE stock_issue_assets (
    issue_id INT NOT NULL,
    asset_id INT NOT NULL,
    PRIMARY KEY (issue_id, asset_id),
    CONSTRAINT fk_stock_issue_assets_issue FOREIGN KEY (issue_id) REFERENCES stock_issues(issue_id),
    CONSTRAINT fk_stock_issue_assets_asset FOREIGN KEY (asset_id) REFERENCES resource_assets(asset_id),
    INDEX idx_stock_issue_assets_asset (asset_id)
);

CREATE TABLE stock_return_assets (
    return_id INT NOT NULL,
    asset_id INT NOT NULL,
    PRIMARY KEY (return_id, asset_id),
    CONSTRAINT fk_stock_return_assets_return FOREIGN KEY (return_id) REFERENCES stock_returns(return_id),
    CONSTRAINT fk_stock_return_assets_asset FOREIGN KEY (asset_id) REFERENCES resource_assets(asset_id),
    INDEX idx_stock_return_assets_asset (asset_id)
);
