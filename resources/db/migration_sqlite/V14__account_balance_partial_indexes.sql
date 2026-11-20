-- Indexes on the latest flag hold only the current balances (WHERE latest = 1). When a new version of
-- a balance is saved, setting latest = 0 on the old one then only removes an index entry instead of
-- moving it, and the indexes stay a fraction of the table's size. The queries filter on the literal
-- latest = 1 (jOOQ renders isTrue() inline), which SQLite needs to use a partial index.
CREATE INDEX IF NOT EXISTS account_balance_id_latest_idx ON account_balance (id) WHERE latest = 1;
DROP INDEX IF EXISTS idx_16411_account_balance_id_latest_idx;
CREATE INDEX IF NOT EXISTS account_balance_balance_latest_idx ON account_balance (balance) WHERE latest = 1;
DROP INDEX IF EXISTS idx_16411_account_balance_latest_balance_idx;

-- Covered by the unique index on (account_id, transaction_id).
DROP INDEX IF EXISTS idx_16538_indirect_incoming_id_index;
