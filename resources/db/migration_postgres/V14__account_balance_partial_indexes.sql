-- Only the current balances in the index (WHERE latest = true), like
-- idx_16411_account_balance_latest_true. When a new version of a balance is saved, setting
-- latest = false on the old one then only removes an index entry instead of moving it.
CREATE INDEX IF NOT EXISTS idx_16411_account_balance_id_latest_true ON account_balance (id) WHERE latest = true;
DROP INDEX IF EXISTS idx_16411_account_balance_id_latest_idx;

-- Covered by the index on (account_id, transaction_id).
DROP INDEX IF EXISTS idx_16538_indirect_incoming_id_index;
