/*
 On April, 2024
 - This model incorporates already all migrations until V10 as done for MariaDB and H2
  As to JDBC Migrations (brs.db.sql.migration) with Flyway further model migrations have to start with V7_4 at least (better V8)
 */

create table if not exists account
(
  db_id           bigserial
    constraint idx_16393_primary
      primary key,
  id              bigint                    not null,
  creation_height bigint                    not null,
  public_key      bytea,
  key_height      bigint,
  name            varchar(100) default NULL::character varying,
  description     text,
  height          bigint                    not null,
  latest          boolean      default true not null
);

create index if not exists idx_16393_account_id_latest_idx
  on account (id, latest);

create index if not exists idx_16393_account_height_idx
  on account (height);

create unique index if not exists idx_16393_account_id_height_idx
  on account (id, height desc);


create table if not exists account_balance
(
  db_id               bigserial
    constraint idx_16411_primary
      primary key,
  id                  bigint               not null,
  balance             bigint               not null,
  unconfirmed_balance bigint               not null,
  forged_balance      bigint               not null,
  height              bigint               not null,
  latest              boolean default true not null
);

create index if not exists idx_16411_account_balance_height_idx
  on account_balance (height);

create index if not exists idx_16411_account_balance_id_latest_idx
  on account_balance (id, latest);

create unique index if not exists idx_16411_account_balance_id_height_idx
  on account_balance (id, height desc);


create table if not exists account_asset
(
  db_id                bigserial
    constraint idx_16404_primary
      primary key,
  account_id           bigint               not null,
  asset_id             bigint               not null,
  quantity             bigint               not null,
  unconfirmed_quantity bigint               not null,
  height               bigint               not null,
  latest               boolean default true not null
);

create index if not exists idx_16404_account_asset_assetid_idx
  on account_asset (latest, asset_id);

create unique index if not exists idx_16404_account_asset_id_height_idx
  on account_asset (account_id, asset_id, height desc);

create index if not exists idx_16404_account_asset_height_idx
  on account_asset (height);

create index if not exists idx_16404_account_asset_quantity_idx
  on account_asset (quantity);

create table if not exists alias
(
  db_id            bigserial
    constraint idx_16418_primary
      primary key,
  id               bigint               not null,
  account_id       bigint               not null,
  alias_name       varchar(100)         not null,
  alias_name_lower varchar(100)         not null,
  alias_uri        text                 not null,
  timestamp        bigint               not null,
  height           bigint               not null,
  latest           boolean default true not null,
  tld              bigint  default '0'::bigint
);

create index if not exists idx_16418_alias_account_id_idx
  on alias (account_id, height desc);

create unique index if not exists idx_16418_alias_id_height_idx
  on alias (id, height desc);

create index if not exists idx_16418_alias_name_lower_tld_idx
  on alias (alias_name_lower, tld, height desc);

create index if not exists idx_16418_alias_latest_idx
  on alias (latest, alias_name_lower);

create index if not exists idx_16418_alias_height_idx
  on alias (height);

create index if not exists idx_16418_alias_name_lower_idx
  on alias (alias_name_lower);

create table if not exists alias_offer
(
  db_id    bigserial
    constraint idx_16429_primary
      primary key,
  id       bigint               not null,
  price    bigint               not null,
  buyer_id bigint,
  height   bigint               not null,
  latest   boolean default true not null
);

create index if not exists idx_16429_alias_offer_height_idx
  on alias_offer (height);

create unique index if not exists idx_16429_alias_offer_id_height_idx
  on alias_offer (id, height desc);

create table if not exists ask_order
(
  db_id           bigserial
    constraint idx_16436_primary
      primary key,
  id              bigint               not null,
  account_id      bigint               not null,
  asset_id        bigint               not null,
  price           bigint               not null,
  quantity        bigint               not null,
  creation_height bigint               not null,
  height          bigint               not null,
  latest          boolean default true not null
);

create index if not exists idx_16436_ask_order_account_id_idx
  on ask_order (account_id, height desc);

create index if not exists idx_16436_ask_order_creation_idx
  on ask_order (creation_height);

create index if not exists idx_16436_ask_order_asset_id_price_idx
  on ask_order (latest, asset_id, price);

create unique index if not exists idx_16436_ask_order_id_height_idx
  on ask_order (id, height desc);

create index if not exists idx_16436_ask_order_height_idx
  on ask_order (height);

create table if not exists asset
(
  db_id       bigserial
    constraint idx_16443_primary
      primary key,
  id          bigint                not null,
  account_id  bigint                not null,
  name        varchar(10)           not null,
  description text,
  quantity    bigint                not null,
  decimals    smallint              not null,
  height      bigint                not null,
  mintable    boolean default false not null
);

create index if not exists idx_16443_asset_height_idx
  on asset (height);

create unique index if not exists idx_16443_asset_id_idx
  on asset (id);

create index if not exists idx_16443_asset_account_id_idx
  on asset (account_id);

create table if not exists asset_transfer
(
  db_id        bigserial
    constraint idx_16453_primary
      primary key,
  id           bigint not null,
  asset_id     bigint not null,
  sender_id    bigint not null,
  recipient_id bigint not null,
  quantity     bigint not null,
  timestamp    bigint not null,
  height       bigint not null
);

create index if not exists idx_16453_asset_transfer_height_idx
  on asset_transfer (height);

create index if not exists idx_16453_asset_transfer_recipient_id_idx
  on asset_transfer (recipient_id, height desc);

create index if not exists idx_16453_asset_transfer_id_idx
  on asset_transfer (id);

create index if not exists idx_16453_asset_transfer_sender_id_idx
  on asset_transfer (sender_id, height desc);

create index if not exists idx_16453_asset_transfer_asset_id_idx
  on asset_transfer (asset_id, height desc);

create table if not exists at
(
  db_id              bigserial
    constraint idx_16459_primary
      primary key,
  id                 bigint                   not null,
  creator_id         bigint                   not null,
  name               varchar(30) default NULL::character varying,
  description        text,
  version            smallint                 not null,
  csize              bigint                   not null,
  dsize              bigint                   not null,
  c_user_stack_bytes bigint                   not null,
  c_call_stack_bytes bigint                   not null,
  creation_height    bigint                   not null,
  ap_code            bytea,
  height             bigint                   not null,
  latest             boolean     default true not null,
  ap_code_hash_id    bigint
);

create index if not exists idx_16459_at_height_idx
  on at (height);

create index if not exists idx_16459_at_ap_code_hash_id_index
  on at (ap_code_hash_id);

create unique index if not exists idx_16459_at_id_height_idx
  on at (id, height desc);

create index if not exists idx_16459_at_latest_idx
  on at (latest, id);

create index if not exists idx_16459_at_creator_id_height_idx
  on at (creator_id, height desc);

create table if not exists at_map
(
  db_id  bigserial
    constraint idx_16470_primary
      primary key,
  at_id  bigint               not null,
  key1   bigint               not null,
  key2   bigint,
  value  bigint,
  height bigint               not null,
  latest boolean default true not null
);

create index if not exists idx_16470_at_map_at_keys_idx
  on at_map (at_id, key1, key2);

create index if not exists idx_16470_at_map_at_latest_idx
  on at_map (at_id, latest);

create index if not exists idx_16470_at_map_height_idx
  on at_map (height);

create table if not exists at_state
(
  db_id                    bigserial
    constraint idx_16477_primary
      primary key,
  at_id                    bigint               not null,
  state                    bytea                not null,
  prev_height              bigint               not null,
  next_height              bigint               not null,
  sleep_between            bigint               not null,
  prev_balance             bigint               not null,
  freeze_when_same_balance boolean              not null,
  min_activate_amount      bigint               not null,
  height                   bigint               not null,
  latest                   boolean default true not null
);

create index if not exists idx_16477_at_state_height_idx
  on at_state (height);

create index if not exists idx_16477_at_state_id_next_height_height_idx
  on at_state (at_id, next_height, height desc);

create index if not exists idx_16477_at_state_id_latest_idx
  on at_state (at_id, latest);

create unique index if not exists idx_16477_at_state_at_id_height_idx
  on at_state (at_id, height desc);

create table if not exists bid_order
(
  db_id           bigserial
    constraint idx_16487_primary
      primary key,
  id              bigint               not null,
  account_id      bigint               not null,
  asset_id        bigint               not null,
  price           bigint               not null,
  quantity        bigint               not null,
  creation_height bigint               not null,
  height          bigint               not null,
  latest          boolean default true not null
);

create index if not exists idx_16487_bid_order_account_id_idx
  on bid_order (account_id, height desc);

create index if not exists idx_16487_bid_order_asset_id_price_idx
  on bid_order (latest, asset_id, price);

create index if not exists idx_16487_bid_order_creation_idx
  on bid_order (creation_height);

create index if not exists idx_16487_bid_order_height_idx
  on bid_order (height);

create unique index if not exists idx_16487_bid_order_id_height_idx
  on bid_order (id, height desc);

create table if not exists block
(
  db_id                 bigserial
    constraint idx_16494_primary
      primary key,
  id                    bigint not null unique,
  version               bigint not null,
  timestamp             bigint not null,
  previous_block_id     bigint,
--     constraint constraint_3c
--       references block (id) on delete cascade,
  total_amount          bigint not null,
  total_fee             bigint not null,
  payload_length        bigint not null,
  generator_public_key  bytea  not null,
  previous_block_hash   bytea,
  cumulative_difficulty bytea  not null,
  base_target           bigint not null,
  next_block_id         bigint,
--     constraint constraint_3c5
--       references block (id) on delete set null,
  height                bigint not null,
  generation_signature  bytea  not null,
  block_signature       bytea  not null,
  payload_hash          bytea  not null,
  generator_id          bigint not null,
  nonce                 bigint not null,
  ats                   bytea,
  total_fee_cash_back   bigint default '0'::bigint,
  total_fee_burnt       bigint default '0'::bigint
);

create index if not exists idx_16494_block_generator_id_height_idx
  on block (generator_id, height desc);

create unique index if not exists idx_16494_block_timestamp_idx
  on block (timestamp desc);

create unique index if not exists idx_16494_block_height_idx
  on block (height);

create index if not exists idx_16494_constraint_3c5
  on block (next_block_id);

create index if not exists idx_16494_constraint_3c
  on block (previous_block_id);

create unique index if not exists idx_16494_block_id_idx
  on block (id);

create index if not exists idx_16494_block_generator_id_idx
  on block (generator_id);

create table if not exists escrow
(
  db_id            bigserial
    constraint idx_16505_primary
      primary key,
  id               bigint               not null,
  sender_id        bigint               not null,
  recipient_id     bigint               not null,
  amount           bigint               not null,
  required_signers bigint,
  deadline         bigint               not null,
  deadline_action  bigint               not null,
  height           bigint               not null,
  latest           boolean default true not null
);

create unique index if not exists idx_16505_escrow_id_height_idx
  on escrow (id, height desc);

create index if not exists idx_16505_escrow_sender_id_height_idx
  on escrow (sender_id, height desc);

create index if not exists idx_16505_escrow_deadline_height_idx
  on escrow (deadline, height desc);

create index if not exists idx_16505_escrow_recipient_id_height_idx
  on escrow (recipient_id, height desc);

create table if not exists escrow_decision
(
  db_id      bigserial
    constraint idx_16512_primary
      primary key,
  escrow_id  bigint               not null,
  account_id bigint               not null,
  decision   bigint               not null,
  height     bigint               not null,
  latest     boolean default true not null
);

create unique index if not exists idx_16512_escrow_decision_escrow_id_account_id_height_idx
  on escrow_decision (escrow_id, account_id, height desc);

create index if not exists idx_16512_escrow_decision_escrow_id_height_idx
  on escrow_decision (escrow_id, height desc);

create index if not exists idx_16512_escrow_decision_account_id_height_idx
  on escrow_decision (account_id, height desc);

create table if not exists goods
(
  db_id       bigserial
    constraint idx_16527_primary
      primary key,
  id          bigint                    not null,
  seller_id   bigint                    not null,
  name        varchar(100)              not null,
  description text,
  tags        varchar(100) default NULL::character varying,
  timestamp   bigint                    not null,
  quantity    bigint                    not null,
  price       bigint                    not null,
  delisted    boolean                   not null,
  height      bigint                    not null,
  latest      boolean      default true not null
);

create index if not exists idx_16527_goods_timestamp_idx
  on goods (timestamp desc, height desc);

create index if not exists idx_16527_goods_seller_id_name_idx
  on goods (seller_id, name);

create unique index if not exists idx_16527_goods_id_height_idx
  on goods (id, height desc);

create table if not exists peer
(
  address varchar(100) not null
    constraint idx_16544_primary
      primary key
);

create table if not exists purchase
(
  db_id                bigserial
    constraint idx_16549_primary
      primary key,
  id                   bigint                not null,
  buyer_id             bigint                not null,
  goods_id             bigint                not null,
  seller_id            bigint                not null,
  quantity             bigint                not null,
  price                bigint                not null,
  deadline             bigint                not null,
  note                 bytea,
  nonce                bytea,
  timestamp            bigint                not null,
  pending              boolean               not null,
  goods                bytea,
  goods_nonce          bytea,
  refund_note          bytea,
  refund_nonce         bytea,
  has_feedback_notes   boolean default false not null,
  has_public_feedbacks boolean default false not null,
  discount             bigint                not null,
  refund               bigint                not null,
  height               bigint                not null,
  latest               boolean default true  not null
);

create index if not exists idx_16549_purchase_deadline_idx
  on purchase (deadline, height desc);

create index if not exists idx_16549_purchase_seller_id_height_idx
  on purchase (seller_id, height desc);

create index if not exists idx_16549_purchase_buyer_id_height_idx
  on purchase (buyer_id, height desc);

create index if not exists idx_16549_purchase_timestamp_idx
  on purchase (timestamp desc, id);

create unique index if not exists idx_16549_purchase_id_height_idx
  on purchase (id, height desc);

create table if not exists purchase_feedback
(
  db_id          bigserial
    constraint idx_16561_primary
      primary key,
  id             bigint               not null,
  feedback_data  bytea                not null,
  feedback_nonce bytea                not null,
  height         bigint               not null,
  latest         boolean default true not null
);

create unique index if not exists idx_16561_purchase_feedback_id_height_idx
  on purchase_feedback (id, height desc);

create table if not exists purchase_public_feedback
(
  db_id           bigserial
    constraint idx_16571_primary
      primary key,
  id              bigint               not null,
  public_feedback text                 not null,
  height          bigint               not null,
  latest          boolean default true not null
);

create unique index if not exists idx_16571_purchase_public_feedback_id_height_idx
  on purchase_public_feedback (id, height desc);

create table if not exists reward_recip_assign
(
  db_id         bigserial
    constraint idx_16581_primary
      primary key,
  account_id    bigint               not null,
  prev_recip_id bigint               not null,
  recip_id      bigint               not null,
  from_height   bigint               not null,
  height        bigint               not null,
  latest        boolean default true not null
);

create index if not exists idx_16581_reward_recip_assign_height_idx
  on reward_recip_assign (height);

create index if not exists idx_16581_reward_recip_assign_recip_id_height_idx
  on reward_recip_assign (recip_id, height desc);

create unique index if not exists idx_16581_reward_recip_assign_account_id_height_idx
  on reward_recip_assign (account_id, height desc);

create table if not exists subscription
(
  db_id        bigserial
    constraint idx_16588_primary
      primary key,
  id           bigint               not null,
  sender_id    bigint               not null,
  recipient_id bigint               not null,
  amount       bigint               not null,
  frequency    bigint               not null,
  time_next    bigint               not null,
  height       bigint               not null,
  latest       boolean default true not null
);

create index if not exists idx_16588_subscription_time_next_index
  on subscription (time_next);

create index if not exists idx_16588_subscription_sender_id_height_idx
  on subscription (sender_id, height desc);

create index if not exists idx_16588_subscription_recipient_id_height_idx
  on subscription (recipient_id, height desc);

create unique index if not exists idx_16588_subscription_id_height_idx
  on subscription (id, height desc);

create index if not exists idx_16588_subscription_latest_index
  on subscription (latest);

create index if not exists idx_16588_subscription_height_idx
  on subscription (height);

create index if not exists idx_16588_subscription_id_latest_idx
  on subscription (id, latest);

create table if not exists trade
(
  db_id            bigserial
    constraint idx_16595_primary
      primary key,
  asset_id         bigint not null,
  block_id         bigint not null,
  ask_order_id     bigint not null,
  bid_order_id     bigint not null,
  ask_order_height bigint not null,
  bid_order_height bigint not null,
  seller_id        bigint not null,
  buyer_id         bigint not null,
  quantity         bigint not null,
  price            bigint not null,
  timestamp        bigint not null,
  height           bigint not null
);

create index if not exists idx_16595_trade_asset_id_idx
  on trade (asset_id, height desc);

create index if not exists idx_16595_trade_seller_id_idx
  on trade (seller_id, height desc);

create index if not exists idx_16595_trade_buyer_id_idx
  on trade (buyer_id, height desc);

create index if not exists idx_16595_trade_height_idx
  on trade (height);

create unique index if not exists idx_16595_trade_ask_bid_idx
  on trade (ask_order_id, bid_order_id);

create table if not exists transaction
(
  db_id                           bigserial
    constraint idx_16601_primary
      primary key,
  id                              bigint                not null unique,
  deadline                        smallint              not null,
  sender_public_key               bytea                 not null,
  recipient_id                    bigint,
  amount                          bigint                not null,
  fee                             bigint                not null,
  height                          bigint                not null,
  block_id                        bigint                not null
    constraint constraint_ff
      references block (id) on delete cascade,
  signature                       bytea,
  timestamp                       bigint                not null,
  type                            smallint              not null,
  subtype                         smallint              not null,
  sender_id                       bigint                not null,
  block_timestamp                 bigint                not null,
  full_hash                       bytea                 not null,
  referenced_transaction_fullhash bytea,
  attachment_bytes                bytea,
  version                         smallint              not null,
  has_message                     boolean default false not null,
  has_encrypted_message           boolean default false not null,
  has_public_key_announcement     boolean default false not null,
  ec_block_height                 bigint,
  ec_block_id                     bigint,
  has_encrypttoself_message       boolean default false not null,
  cash_back_id                    bigint  default '0'::bigint
);

create index if not exists idx_16601_transaction_recipient_id_idx
  on transaction (recipient_id);

create index if not exists idx_16601_constraint_ff
  on transaction (block_id);

create index if not exists idx_16601_transaction_ref_tx_fullhash_idx
  on transaction (referenced_transaction_fullhash);

create unique index if not exists idx_16601_transaction_full_hash_idx
  on transaction (full_hash);

create index if not exists idx_16601_transaction_block_timestamp_idx
  on transaction (block_timestamp);

create index if not exists idx_16601_transaction_type_subtype_idx
  on transaction (type, subtype);

create index if not exists idx_16601_transaction_height_idx
  on transaction (height);

create index if not exists idx_16601_tx_cash_back_index
  on transaction (cash_back_id);

create index if not exists idx_16601_tx_sender_type
  on transaction (sender_id, type);

create unique index if not exists idx_16601_transaction_id_idx
  on transaction (id);

create index if not exists idx_16601_tx_block_id_index
  on transaction (block_id);

create index if not exists idx_16601_transaction_recipient_id_amount_height_idx
  on transaction (recipient_id, amount, height desc);

create index if not exists idx_16601_transaction_sender_id_idx
  on transaction (sender_id);

create table if not exists indirect_incoming
(
  db_id          bigserial
    constraint idx_16538_primary
      primary key,
  account_id     bigint not null,
  transaction_id bigint not null,
  height         bigint not null,
  amount         bigint default '0'::bigint,
  quantity       bigint default '0'::bigint
);

create index if not exists idx_16538_indirect_incoming_id_index
  on indirect_incoming (account_id);

create index if not exists idx_16538_indirect_incoming_height_idx
  on indirect_incoming (height);

create index if not exists idx_16538_indirect_incoming_db_id_uindex
  on indirect_incoming (account_id, transaction_id);

create index if not exists idx_16538_indirect_incoming_tx_idx
  on indirect_incoming (transaction_id);

create table if not exists unconfirmed_transaction
(
  db_id              bigserial
    constraint idx_16615_primary
      primary key,
  id                 bigint not null,
  expiration         bigint not null,
  transaction_height bigint not null,
  fee_per_byte       bigint not null,
  timestamp          bigint not null,
  transaction_bytes  bytea  not null,
  height             bigint not null
);

create unique index if not exists idx_16615_unconfirmed_transaction_id_idx
  on unconfirmed_transaction (id);

create index if not exists idx_16615_unconfirmed_transaction_height_fee_timestamp_idx
  on unconfirmed_transaction (transaction_height, fee_per_byte, timestamp desc);

