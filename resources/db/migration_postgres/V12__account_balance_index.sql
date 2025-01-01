DROP INDEX IF EXISTS idx_16404_account_asset_assetid_idx;
CREATE INDEX IF NOT EXISTS idx_16404_account_asset_assetid_latest_idx ON account_asset (account_id, asset_id, latest);