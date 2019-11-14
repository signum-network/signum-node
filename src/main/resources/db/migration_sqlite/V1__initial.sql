PRAGMA foreign_keys = ON;
CREATE TABLE block (db_id IDENTITY, id BIGINT NOT NULL, version INT NOT NULL,
    timestamp INT NOT NULL, previous_block_id BIGINT REFERENCES block(id) ON DELETE CASCADE, total_amount BIGINT NOT NULL,
    total_fee BIGINT NOT NULL, payload_length INT NOT NULL, generator_public_key BINARY(32) NOT NULL,
    previous_block_hash BINARY(32), cumulative_difficulty VARBINARY NOT NULL, base_target BIGINT NOT NULL,
    next_block_id BIGINT REFERENCES block (id) ON DELETE SET NULL, height INT NOT NULL, generation_signature BINARY(64) NOT NULL,
    block_signature BINARY(64) NOT NULL, payload_hash BINARY(32) NOT NULL, generator_id BIGINT NOT NULL, nonce BIGINT NOT NULL, ats BINARY);
CREATE UNIQUE INDEX block_id_idx ON block (id);
CREATE TABLE "transaction" (db_id IDENTITY, id BIGINT NOT NULL,
    deadline SMALLINT NOT NULL, sender_public_key BINARY(32) NOT NULL, recipient_id BIGINT NULL,
    amount BIGINT NOT NULL, fee BIGINT NOT NULL,
    height INT NOT NULL, block_id BIGINT NOT NULL REFERENCES block (id) ON DELETE CASCADE,
    signature BINARY(64) NULL, timestamp INT NOT NULL, type TINYINT NOT NULL, subtype TINYINT NOT NULL,
    sender_id BIGINT NOT NULL, attachment_bytes VARBINARY, block_timestamp INT NOT NULL, full_hash BINARY(32) NOT NULL,
    referenced_transaction_fullhash BINARY(32), version TINYINT NOT NULL, has_message BOOLEAN NOT NULL DEFAULT FALSE,
    has_encrypted_message BOOLEAN NOT NULL DEFAULT FALSE, has_public_key_announcement BOOLEAN NOT NULL DEFAULT FALSE,
    ec_block_height INT DEFAULT NULL, ec_block_id BIGINT DEFAULT NULL, has_encrypttoself_message BOOLEAN NOT NULL DEFAULT FALSE);
CREATE UNIQUE INDEX transaction_id_idx ON "transaction" (id);
CREATE UNIQUE INDEX block_height_idx ON block (height);
CREATE INDEX block_generator_id_idx ON block (generator_id);
CREATE INDEX transaction_sender_id_idx ON "transaction" (sender_id);
CREATE TABLE peer (address VARCHAR PRIMARY KEY);
CREATE UNIQUE INDEX transaction_full_hash_idx ON "transaction" (full_hash);
CREATE INDEX transaction_recipient_id_idx ON "transaction" (recipient_id);
CREATE INDEX transaction_block_timestamp_idx ON "transaction" (block_timestamp DESC);
CREATE TABLE alias (db_id IDENTITY, id BIGINT NOT NULL,
    account_id BIGINT NOT NULL, alias_name VARCHAR NOT NULL,
    alias_name_lower VARCHAR NOT NULL,
    alias_uri VARCHAR NOT NULL, timestamp INT NOT NULL,
    height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX alias_id_height_idx ON alias (id, height DESC);
CREATE INDEX alias_account_id_idx ON alias (account_id, height DESC);
CREATE INDEX alias_name_lower_idx ON alias (alias_name_lower);
CREATE TABLE alias_offer (db_id IDENTITY, id BIGINT NOT NULL,
    price BIGINT NOT NULL, buyer_id BIGINT,
    height INT NOT NULL, latest BOOLEAN DEFAULT TRUE NOT NULL);
CREATE UNIQUE INDEX alias_offer_id_height_idx ON alias_offer (id, height DESC);
CREATE TABLE asset (db_id IDENTITY, id BIGINT NOT NULL, account_id BIGINT NOT NULL,
    name VARCHAR NOT NULL, description VARCHAR, quantity BIGINT NOT NULL, decimals TINYINT NOT NULL,
    height INT NOT NULL);
CREATE UNIQUE INDEX asset_id_idx ON asset (id);
CREATE INDEX asset_account_id_idx ON asset (account_id);
CREATE TABLE trade (db_id IDENTITY, asset_id BIGINT NOT NULL, block_id BIGINT NOT NULL,
    ask_order_id BIGINT NOT NULL, bid_order_id BIGINT NOT NULL, ask_order_height INT NOT NULL,
    bid_order_height INT NOT NULL, seller_id BIGINT NOT NULL, buyer_id BIGINT NOT NULL,
    quantity BIGINT NOT NULL, price BIGINT NOT NULL, timestamp INT NOT NULL, height INT NOT NULL);
CREATE UNIQUE INDEX trade_ask_bid_idx ON trade (ask_order_id, bid_order_id);
CREATE INDEX trade_asset_id_idx ON trade (asset_id, height DESC);
CREATE INDEX trade_seller_id_idx ON trade (seller_id, height DESC);
CREATE INDEX trade_buyer_id_idx ON trade (buyer_id, height DESC);
CREATE TABLE ask_order (db_id IDENTITY, id BIGINT NOT NULL, account_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL, price BIGINT NOT NULL,
    quantity BIGINT NOT NULL, creation_height INT NOT NULL, height INT NOT NULL,
    latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX ask_order_id_height_idx ON ask_order (id, height DESC);
CREATE INDEX ask_order_account_id_idx ON ask_order (account_id, height DESC);
CREATE INDEX ask_order_asset_id_price_idx ON ask_order (asset_id, price);
CREATE TABLE bid_order (db_id IDENTITY, id BIGINT NOT NULL, account_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL, price BIGINT NOT NULL,
    quantity BIGINT NOT NULL, creation_height INT NOT NULL, height INT NOT NULL,
    latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX bid_order_id_height_idx ON bid_order (id, height DESC);
CREATE INDEX bid_order_account_id_idx ON bid_order (account_id, height DESC);
CREATE INDEX bid_order_asset_id_price_idx ON bid_order (asset_id, price DESC);
CREATE TABLE goods (db_id IDENTITY, id BIGINT NOT NULL, seller_id BIGINT NOT NULL,
    name VARCHAR NOT NULL, description VARCHAR,
    tags VARCHAR, timestamp INT NOT NULL, quantity INT NOT NULL, price BIGINT NOT NULL,
    delisted BOOLEAN NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX goods_id_height_idx ON goods (id, height DESC);
CREATE INDEX goods_seller_id_name_idx ON goods (seller_id, name);
CREATE INDEX goods_timestamp_idx ON goods (timestamp DESC, height DESC);
CREATE TABLE purchase (db_id IDENTITY, id BIGINT NOT NULL, buyer_id BIGINT NOT NULL,
    goods_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL, quantity INT NOT NULL,
    price BIGINT NOT NULL, deadline INT NOT NULL, note VARBINARY, nonce BINARY(32),
    timestamp INT NOT NULL, pending BOOLEAN NOT NULL, goods VARBINARY, goods_nonce BINARY(32),
    refund_note VARBINARY, refund_nonce BINARY(32), has_feedback_notes BOOLEAN NOT NULL DEFAULT FALSE,
    has_public_feedbacks BOOLEAN NOT NULL DEFAULT FALSE, discount BIGINT NOT NULL, refund BIGINT NOT NULL,
    height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX purchase_id_height_idx ON purchase (id, height DESC);
CREATE INDEX purchase_buyer_id_height_idx ON purchase (buyer_id, height DESC);
CREATE INDEX purchase_seller_id_height_idx ON purchase (seller_id, height DESC);
CREATE INDEX purchase_deadline_idx ON purchase (deadline DESC, height DESC);
CREATE TABLE account (db_id IDENTITY, id BIGINT NOT NULL, creation_height INT NOT NULL,
    public_key BINARY(32), key_height INT, balance BIGINT NOT NULL, unconfirmed_balance BIGINT NOT NULL,
    forged_balance BIGINT NOT NULL, name VARCHAR, description VARCHAR, height INT NOT NULL,
    latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX account_id_height_idx ON account (id, height DESC);
CREATE TABLE account_asset (db_id IDENTITY, account_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL, quantity BIGINT NOT NULL, unconfirmed_quantity BIGINT NOT NULL, height INT NOT NULL,
    latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX account_asset_id_height_idx ON account_asset (account_id, asset_id, height DESC);
CREATE TABLE purchase_feedback (db_id IDENTITY, id BIGINT NOT NULL, feedback_data VARBINARY NOT NULL,
    feedback_nonce BINARY(32) NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE INDEX purchase_feedback_id_height_idx ON purchase_feedback (id, height DESC);
CREATE TABLE purchase_public_feedback (db_id IDENTITY, id BIGINT NOT NULL, public_feedback
    VARCHAR NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE INDEX purchase_public_feedback_id_height_idx ON purchase_public_feedback (id, height DESC);
CREATE TABLE unconfirmed_transaction (db_id IDENTITY, id BIGINT NOT NULL, expiration INT NOT NULL,
    transaction_height INT NOT NULL, fee_per_byte BIGINT NOT NULL, timestamp INT NOT NULL,
    transaction_bytes VARBINARY NOT NULL, height INT NOT NULL);
CREATE UNIQUE INDEX unconfirmed_transaction_id_idx ON unconfirmed_transaction (id);
CREATE INDEX unconfirmed_transaction_height_fee_timestamp_idx ON unconfirmed_transaction
    (transaction_height ASC, fee_per_byte DESC, timestamp ASC);
CREATE TABLE asset_transfer (db_id IDENTITY, id BIGINT NOT NULL, asset_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL, quantity BIGINT NOT NULL, timestamp INT NOT NULL,
    height INT NOT NULL);
CREATE UNIQUE INDEX asset_transfer_id_idx ON asset_transfer (id);
CREATE INDEX asset_transfer_asset_id_idx ON asset_transfer (asset_id, height DESC);
CREATE INDEX asset_transfer_sender_id_idx ON asset_transfer (sender_id, height DESC);
CREATE INDEX asset_transfer_recipient_id_idx ON asset_transfer (recipient_id, height DESC);
CREATE INDEX account_asset_quantity_idx ON account_asset (quantity DESC);
CREATE INDEX purchase_timestamp_idx ON purchase (timestamp DESC, id);
CREATE INDEX ask_order_creation_idx ON ask_order (creation_height DESC);
CREATE INDEX bid_order_creation_idx ON bid_order (creation_height DESC);
CREATE TABLE reward_recip_assign (db_id IDENTITY, account_id BIGINT NOT NULL,
    prev_recip_id BIGINT NOT NULL, recip_id BIGINT NOT NULL, from_height INT NOT NULL,
    height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX reward_recip_assign_account_id_height_idx ON reward_recip_assign (account_id, height DESC);
CREATE INDEX reward_recip_assign_recip_id_height_idx ON reward_recip_assign (recip_id, height DESC);
CREATE TABLE escrow (db_id IDENTITY, id BIGINT NOT NULL, sender_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL,
    amount BIGINT NOT NULL, required_signers INT, deadline INT NOT NULL, deadline_action INT NOT NULL,
    height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX escrow_id_height_idx ON escrow (id, height DESC);
CREATE INDEX escrow_sender_id_height_idx ON escrow (sender_id, height DESC);
CREATE INDEX escrow_recipient_id_height_idx ON escrow (recipient_id, height DESC);
CREATE INDEX escrow_deadline_height_idx ON escrow (deadline, height DESC);
CREATE TABLE escrow_decision (db_id IDENTITY, escrow_id BIGINT NOT NULL, account_id BIGINT NOT NULL,
    decision INT NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX escrow_decision_escrow_id_account_id_height_idx ON escrow_decision (escrow_id, account_id, height DESC);
CREATE INDEX escrow_decision_escrow_id_height_idx ON escrow_decision (escrow_id, height DESC);
CREATE INDEX escrow_decision_account_id_height_idx ON escrow_decision (account_id, height DESC);
CREATE TABLE subscription (db_id IDENTITY, id BIGINT NOT NULL, sender_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL,
    amount BIGINT NOT NULL, frequency INT NOT NULL, time_next INT NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX subscription_id_height_idx ON subscription (id, height DESC);
CREATE INDEX subscription_sender_id_height_idx ON subscription (sender_id, height DESC);
CREATE INDEX subscription_recipient_id_height_idx ON subscription (recipient_id, height DESC);
CREATE UNIQUE INDEX block_timestamp_idx ON block (timestamp DESC);
CREATE TABLE at (db_id IDENTITY, id BIGINT NOT NULL, creator_id BIGINT NOT NULL, name VARCHAR, description VARCHAR,
    version SMALLINT NOT NULL, csize INT NOT NULL, dsize INT NOT NULL, c_user_stack_bytes INT NOT NULL, c_call_stack_bytes INT NOT NULL,
    creation_height INT NOT NULL, ap_code BINARY NOT NULL,
    height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX at_id_height_idx ON at (id, height DESC);
CREATE INDEX at_creator_id_height_idx ON at (creator_id, height DESC);
CREATE TABLE at_state (db_id IDENTITY, at_id BIGINT NOT NULL, state BINARY NOT NULL, prev_height INT NOT NULL,
    next_height INT NOT NULL, sleep_between INT NOT NULL,
    prev_balance BIGINT NOT NULL, freeze_when_same_balance BOOLEAN NOT NULL, min_activate_amount BIGINT NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE);
CREATE UNIQUE INDEX at_state_at_id_height_idx ON at_state (at_id, height DESC);
CREATE INDEX at_state_id_next_height_height_idx ON at_state (at_id, next_height, height DESC);
CREATE INDEX account_id_balance_height_idx ON account (id, balance, height DESC);
CREATE INDEX transaction_recipient_id_amount_height_idx ON "transaction" (recipient_id, amount, height);
CREATE INDEX account_id_latest_idx ON account(id, latest);
