DROP INDEX IF EXISTS account_asset_assetid_idx;
CREATE INDEX IF NOT EXISTS account_asset_assetid_latest_idx ON account_asset (account_id, asset_id, latest);