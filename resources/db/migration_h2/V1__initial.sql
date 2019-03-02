/* Created by performing all steps of old migration */
CREATE TABLE IF NOT EXISTS block (db_id IDENTITY, id BIGINT NOT NULL, version INT NOT NULL,
    timestamp INT NOT NULL, previous_block_id BIGINT,
    FOREIGN KEY (previous_block_id) REFERENCES block (id) ON DELETE CASCADE, total_amount INT NOT NULL,
    total_fee INT NOT NULL, payload_length INT NOT NULL, generator_public_key BINARY(32) NOT NULL,
    previous_block_hash BINARY(32), cumulative_difficulty VARBINARY NOT NULL, base_target BIGINT NOT NULL,
    next_block_id BIGINT, FOREIGN KEY (next_block_id) REFERENCES block (id) ON DELETE SET NULL,
    index INT NOT NULL, height INT NOT NULL, generation_signature BINARY(64) NOT NULL,
    block_signature BINARY(64) NOT NULL, payload_hash BINARY(32) NOT NULL, generator_account_id BIGINT NOT NULL, nonce BIGINT NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS block_id_idx ON block (id);
CREATE TABLE IF NOT EXISTS transaction (db_id IDENTITY, id BIGINT NOT NULL,
    deadline SMALLINT NOT NULL, sender_public_key BINARY(32) NOT NULL, recipient_id BIGINT NOT NULL,
    amount INT NOT NULL, fee INT NOT NULL, referenced_transaction_id BIGINT, index INT NOT NULL,
    height INT NOT NULL, block_id BIGINT NOT NULL, FOREIGN KEY (block_id) REFERENCES block (id) ON DELETE CASCADE,
    signature BINARY(64) NOT NULL, timestamp INT NOT NULL, type TINYINT NOT NULL, subtype TINYINT NOT NULL,
    sender_account_id BIGINT NOT NULL, attachment OTHER);
CREATE UNIQUE INDEX IF NOT EXISTS transaction_id_idx ON transaction (id);
CREATE UNIQUE INDEX IF NOT EXISTS block_height_idx ON block (height);
CREATE INDEX IF NOT EXISTS transaction_timestamp_idx ON transaction (timestamp);
CREATE INDEX IF NOT EXISTS block_generator_account_id_idx ON block (generator_account_id);
CREATE INDEX IF NOT EXISTS transaction_sender_account_id_idx ON transaction (sender_account_id);
CREATE INDEX IF NOT EXISTS transaction_recipient_id_idx ON transaction (recipient_id);
ALTER TABLE block ALTER COLUMN generator_account_id RENAME TO generator_id;
ALTER TABLE transaction ALTER COLUMN sender_account_id RENAME TO sender_id;
ALTER INDEX block_generator_account_id_idx RENAME TO block_generator_id_idx;
ALTER INDEX transaction_sender_account_id_idx RENAME TO transaction_sender_id_idx;
ALTER TABLE block DROP COLUMN IF EXISTS index;
ALTER TABLE transaction DROP COLUMN IF EXISTS index;
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS block_timestamp INT;
ALTER TABLE transaction ALTER COLUMN block_timestamp SET NOT NULL;
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS hash BINARY(32);
CREATE INDEX IF NOT EXISTS transaction_hash_idx ON transaction (hash);
ALTER TABLE block ALTER COLUMN total_amount BIGINT;
ALTER TABLE block ALTER COLUMN total_fee BIGINT;
ALTER TABLE transaction ALTER COLUMN amount BIGINT;
ALTER TABLE transaction ALTER COLUMN fee BIGINT;
CREATE TABLE IF NOT EXISTS peer (address VARCHAR PRIMARY KEY);
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS full_hash BINARY(32);
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS referenced_transaction_full_hash BINARY(32);
ALTER TABLE transaction ALTER COLUMN full_hash SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS transaction_full_hash_idx ON transaction (full_hash);
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS attachment_bytes VARBINARY;
ALTER TABLE transaction DROP COLUMN attachment;
ALTER TABLE transaction DROP COLUMN referenced_transaction_id;
ALTER TABLE transaction DROP COLUMN hash;
DROP INDEX transaction_recipient_id_idx;
ALTER TABLE transaction ALTER COLUMN recipient_id SET NULL;
CREATE INDEX IF NOT EXISTS transaction_recipient_id_idx ON transaction (recipient_id);
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS version TINYINT;
UPDATE transaction SET version = 0;
ALTER TABLE transaction ALTER COLUMN version SET NOT NULL;
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS has_message BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS has_encrypted_message BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE transaction SET has_message = TRUE WHERE type = 1 AND subtype = 0;
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS has_public_key_announcement BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS ec_block_height INT DEFAULT NULL;
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS ec_block_id BIGINT DEFAULT NULL;
ALTER TABLE transaction ADD COLUMN IF NOT EXISTS has_encrypttoself_message BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS transaction_block_timestamp_idx ON transaction (block_timestamp DESC);
DROP INDEX transaction_timestamp_idx;
CREATE TABLE IF NOT EXISTS alias (db_id IDENTITY, id BIGINT NOT NULL,
    account_id BIGINT NOT NULL, alias_name VARCHAR NOT NULL,
    alias_name_lower VARCHAR AS LOWER (alias_name) NOT NULL,
    alias_uri VARCHAR NOT NULL, timestamp INT NOT NULL,
    height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX IF NOT EXISTS alias_id_height_idx ON alias (id, height DESC);
CREATE INDEX IF NOT EXISTS alias_account_id_idx ON alias (account_id, height DESC);
CREATE INDEX IF NOT EXISTS alias_name_lower_idx ON alias (alias_name_lower);
CREATE TABLE IF NOT EXISTS alias_offer (db_id IDENTITY, id BIGINT NOT NULL,
    price BIGINT NOT NULL, buyer_id BIGINT,
    height INT NOT NULL, latest BOOLEAN DEFAULT TRUE NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS alias_offer_id_height_idx ON alias_offer (id, height DESC);
CREATE TABLE IF NOT EXISTS asset (db_id IDENTITY, id BIGINT NOT NULL, account_id BIGINT NOT NULL,
    name VARCHAR NOT NULL, description VARCHAR, quantity BIGINT NOT NULL, decimals TINYINT NOT NULL,
    height INT NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS asset_id_idx ON asset (id);
CREATE INDEX IF NOT EXISTS asset_account_id_idx ON asset (account_id);
CREATE TABLE IF NOT EXISTS trade (db_id IDENTITY, asset_id BIGINT NOT NULL, block_id BIGINT NOT NULL,
    ask_order_id BIGINT NOT NULL, bid_order_id BIGINT NOT NULL, ask_order_height INT NOT NULL,
    bid_order_height INT NOT NULL, seller_id BIGINT NOT NULL, buyer_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL, price BIGINT NOT NULL, timestamp INT NOT NULL, height INT NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS trade_ask_bid_idx ON trade (ask_order_id, bid_order_id);
CREATE INDEX IF NOT EXISTS trade_asset_id_idx ON trade (asset_id, height DESC);
CREATE INDEX IF NOT EXISTS trade_seller_id_idx ON trade (seller_id, height DESC);
CREATE INDEX IF NOT EXISTS trade_buyer_id_idx ON trade (buyer_id, height DESC);
CREATE TABLE IF NOT EXISTS ask_order (db_id IDENTITY, id BIGINT NOT NULL, account_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL, price BIGINT NOT NULL,
    quantity BIGINT NOT NULL, creation_height INT NOT NULL, height INT NOT NULL,
    latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX IF NOT EXISTS ask_order_id_height_idx ON ask_order (id, height DESC);
CREATE INDEX IF NOT EXISTS ask_order_account_id_idx ON ask_order (account_id, height DESC);
CREATE INDEX IF NOT EXISTS ask_order_asset_id_price_idx ON ask_order (asset_id, price);
CREATE TABLE IF NOT EXISTS bid_order (db_id IDENTITY, id BIGINT NOT NULL, account_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL, price BIGINT NOT NULL,
    quantity BIGINT NOT NULL, creation_height INT NOT NULL, height INT NOT NULL,
    latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX IF NOT EXISTS bid_order_id_height_idx ON bid_order (id, height DESC);
CREATE INDEX IF NOT EXISTS bid_order_account_id_idx ON bid_order (account_id, height DESC);
CREATE INDEX IF NOT EXISTS bid_order_asset_id_price_idx ON bid_order (asset_id, price DESC);
CREATE TABLE IF NOT EXISTS goods (db_id IDENTITY, id BIGINT NOT NULL, seller_id BIGINT NOT NULL,
    name VARCHAR NOT NULL, description VARCHAR,
    tags VARCHAR, timestamp INT NOT NULL, quantity INT NOT NULL, price BIGINT NOT NULL,
    delisted BOOLEAN NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX IF NOT EXISTS goods_id_height_idx ON goods (id, height DESC);
CREATE INDEX IF NOT EXISTS goods_seller_id_name_idx ON goods (seller_id, name);
CREATE INDEX IF NOT EXISTS goods_timestamp_idx ON goods (timestamp DESC, height DESC);
CREATE TABLE IF NOT EXISTS purchase (db_id IDENTITY, id BIGINT NOT NULL, buyer_id BIGINT NOT NULL,
    goods_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL, quantity INT NOT NULL,
    price BIGINT NOT NULL, deadline INT NOT NULL, note VARBINARY, nonce BINARY(32),
    timestamp INT NOT NULL, pending BOOLEAN NOT NULL, goods VARBINARY, goods_nonce BINARY(32),
    refund_note VARBINARY, refund_nonce BINARY(32), has_feedback_notes BOOLEAN NOT NULL DEFAULT FALSE,
    has_public_feedbacks BOOLEAN NOT NULL DEFAULT FALSE, discount BIGINT NOT NULL, refund BIGINT NOT NULL,
    height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX IF NOT EXISTS purchase_id_height_idx ON purchase (id, height DESC);
CREATE INDEX IF NOT EXISTS purchase_buyer_id_height_idx ON purchase (buyer_id, height DESC);
CREATE INDEX IF NOT EXISTS purchase_seller_id_height_idx ON purchase (seller_id, height DESC);
CREATE INDEX IF NOT EXISTS purchase_deadline_idx ON purchase (deadline DESC, height DESC);
CREATE TABLE IF NOT EXISTS account (db_id IDENTITY, id BIGINT NOT NULL, creation_height INT NOT NULL,
    public_key BINARY(32), key_height INT, balance BIGINT NOT NULL, unconfirmed_balance BIGINT NOT NULL,
    forged_balance BIGINT NOT NULL, name VARCHAR, description VARCHAR, current_leasing_height_from INT,
    current_leasing_height_to INT, current_lessee_id BIGINT NULL, next_leasing_height_from INT,
    next_leasing_height_to INT, next_lessee_id BIGINT NULL, height INT NOT NULL,
    latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX IF NOT EXISTS account_id_height_idx ON account (id, height DESC);
CREATE TABLE IF NOT EXISTS account_asset (db_id IDENTITY, account_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL, quantity BIGINT NOT NULL, unconfirmed_quantity BIGINT NOT NULL, height INT NOT NULL,
    latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX IF NOT EXISTS account_asset_id_height_idx ON account_asset (account_id, asset_id, height DESC);
CREATE TABLE IF NOT EXISTS purchase_feedback (db_id IDENTITY, id BIGINT NOT NULL, feedback_data VARBINARY NOT NULL,
    feedback_nonce BINARY(32) NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE INDEX IF NOT EXISTS purchase_feedback_id_height_idx ON purchase_feedback (id, height DESC);
CREATE TABLE IF NOT EXISTS purchase_public_feedback (db_id IDENTITY, id BIGINT NOT NULL, public_feedback
    VARCHAR NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE INDEX IF NOT EXISTS purchase_public_feedback_id_height_idx ON purchase_public_feedback (id, height DESC);
CREATE TABLE IF NOT EXISTS unconfirmed_transaction (db_id IDENTITY, id BIGINT NOT NULL, expiration INT NOT NULL,
    transaction_height INT NOT NULL, fee_per_byte BIGINT NOT NULL, timestamp INT NOT NULL,
    transaction_bytes VARBINARY NOT NULL, height INT NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS unconfirmed_transaction_id_idx ON unconfirmed_transaction (id);
CREATE INDEX IF NOT EXISTS unconfirmed_transaction_height_fee_timestamp_idx ON unconfirmed_transaction
    (transaction_height ASC, fee_per_byte DESC, timestamp ASC);
CREATE TABLE IF NOT EXISTS asset_transfer (db_id IDENTITY, id BIGINT NOT NULL, asset_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL, quantity BIGINT NOT NULL, timestamp INT NOT NULL,
    height INT NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS asset_transfer_id_idx ON asset_transfer (id);
CREATE INDEX IF NOT EXISTS asset_transfer_asset_id_idx ON asset_transfer (asset_id, height DESC);
CREATE INDEX IF NOT EXISTS asset_transfer_sender_id_idx ON asset_transfer (sender_id, height DESC);
CREATE INDEX IF NOT EXISTS asset_transfer_recipient_id_idx ON asset_transfer (recipient_id, height DESC);
CREATE INDEX IF NOT EXISTS account_asset_quantity_idx ON account_asset (quantity DESC);
CREATE INDEX IF NOT EXISTS purchase_timestamp_idx ON purchase (timestamp DESC, id);
CREATE INDEX IF NOT EXISTS ask_order_creation_idx ON ask_order (creation_height DESC);
CREATE INDEX IF NOT EXISTS bid_order_creation_idx ON bid_order (creation_height DESC);
CREATE TABLE IF NOT EXISTS reward_recip_assign (db_id IDENTITY, account_id BIGINT NOT NULL,
    prev_recip_id BIGINT NOT NULL, recip_id BIGINT NOT NULL, from_height INT NOT NULL,
    height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX IF NOT EXISTS reward_recip_assign_account_id_height_idx ON reward_recip_assign (account_id, height DESC);
CREATE INDEX IF NOT EXISTS reward_recip_assign_recip_id_height_idx ON reward_recip_assign (recip_id, height DESC);
CREATE TABLE IF NOT EXISTS escrow (db_id IDENTITY, id BIGINT NOT NULL, sender_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL,
    amount BIGINT NOT NULL, required_signers INT, deadline INT NOT NULL, deadline_action INT NOT NULL,
    height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX IF NOT EXISTS escrow_id_height_idx ON escrow (id, height DESC);
CREATE INDEX IF NOT EXISTS escrow_sender_id_height_idx ON escrow (sender_id, height DESC);
CREATE INDEX IF NOT EXISTS escrow_recipient_id_height_idx ON escrow (recipient_id, height DESC);
CREATE INDEX IF NOT EXISTS escrow_deadline_height_idx ON escrow (deadline, height DESC);
CREATE TABLE IF NOT EXISTS escrow_decision (db_id IDENTITY, escrow_id BIGINT NOT NULL, account_id BIGINT NOT NULL,
    decision INT NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX IF NOT EXISTS escrow_decision_escrow_id_account_id_height_idx ON escrow_decision (escrow_id, account_id, height DESC);
CREATE INDEX IF NOT EXISTS escrow_decision_escrow_id_height_idx ON escrow_decision (escrow_id, height DESC);
CREATE INDEX IF NOT EXISTS escrow_decision_account_id_height_idx ON escrow_decision (account_id, height DESC);
ALTER TABLE transaction ALTER COLUMN signature SET NULL;
CREATE TABLE IF NOT EXISTS subscription (db_id IDENTITY, id BIGINT NOT NULL, sender_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL,
    amount BIGINT NOT NULL, frequency INT NOT NULL, time_next INT NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX IF NOT EXISTS subscription_id_height_idx ON subscription (id, height DESC);
CREATE INDEX IF NOT EXISTS subscription_sender_id_height_idx ON subscription (sender_id, height DESC);
CREATE INDEX IF NOT EXISTS subscription_recipient_id_height_idx ON subscription (recipient_id, height DESC);
CREATE UNIQUE INDEX IF NOT EXISTS block_timestamp_idx ON block (timestamp DESC);
CREATE TABLE IF NOT EXISTS at (db_id IDENTITY, id BIGINT NOT NULL, creator_id BIGINT NOT NULL, name VARCHAR, description VARCHAR,
    version SMALLINT NOT NULL, csize INT NOT NULL, dsize INT NOT NULL, c_user_stack_bytes INT NOT NULL, c_call_stack_bytes INT NOT NULL,
    creation_height INT NOT NULL, ap_code BINARY NOT NULL,
    height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX IF NOT EXISTS at_id_height_idx ON at (id, height DESC);
CREATE INDEX IF NOT EXISTS at_creator_id_height_idx ON at (creator_id, height DESC);
CREATE TABLE IF NOT EXISTS at_state (db_id IDENTITY, at_id BIGINT NOT NULL, state BINARY NOT NULL, prev_height INT NOT NULL,
    next_height INT NOT NULL, sleep_between INT NOT NULL,
    prev_balance BIGINT NOT NULL, freeze_when_same_balance BOOLEAN NOT NULL, min_activate_amount BIGINT NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX IF NOT EXISTS at_state_at_id_height_idx ON at_state (at_id, height DESC);
CREATE INDEX IF NOT EXISTS at_state_id_next_height_height_idx ON at_state (at_id, next_height, height DESC);
ALTER TABLE block ADD COLUMN IF NOT EXISTS ats BINARY;
CREATE INDEX IF NOT EXISTS account_id_balance_height_idx ON account (id, balance, height DESC);
CREATE INDEX IF NOT EXISTS transaction_recipient_id_amount_height_idx ON transaction (recipient_id, amount, height);
DROP INDEX IF EXISTS account_guaranteed_balance_id_height_idx;
DROP TABLE IF EXISTS account_guaranteed_balance;
DROP INDEX IF EXISTS account_current_lessee_id_leasing_height_idx;
ALTER TABLE account DROP COLUMN IF EXISTS current_leasing_height_from;
ALTER TABLE account DROP COLUMN IF EXISTS current_leasing_height_to;
ALTER TABLE account DROP COLUMN IF EXISTS current_lessee_id;
ALTER TABLE account DROP COLUMN IF EXISTS next_leasing_height_from;
ALTER TABLE account DROP COLUMN IF EXISTS next_leasing_height_to;
ALTER TABLE account DROP COLUMN IF EXISTS next_lessee_id;
ALTER TABLE transaction ALTER COLUMN referenced_transaction_full_hash RENAME TO referenced_transaction_fullhash;
ALTER TABLE alias ALTER COLUMN alias_name_LOWER RENAME TO alias_name_lower;
CREATE INDEX IF NOT EXISTS account_id_latest_idx ON account(id, latest);
ALTER TABLE alias ALTER COLUMN alias_name_lower VARCHAR NOT NULL;