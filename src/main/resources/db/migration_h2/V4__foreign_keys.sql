-- account

-- h2 required not composite key for link foreign key
create index ACCOUNT_ID_INDEX
	on "account" ("id");

-- account_asset

alter table "account_asset"
	add constraint ACCOUNT_ASSET_ACCOUNT_ID_FK
		foreign key ("account_id") references "account" ("id")
			on delete cascade;

alter table "account_asset"
	add constraint ACCOUNT_ASSET_ASSET_ID_FK
		foreign key ("asset_id") references "asset" ("id")
			on delete cascade;

-- alias

alter table "alias"
	add constraint ALIAS_TRANSACTION_ID_FK
		foreign key ("id") references "transaction" ("id")
			on delete cascade;

alter table "alias"
	add constraint ALIAS_ACCOUNT_ID_FK
		foreign key ("account_id") references "account" ("id")
			on delete cascade;

-- alias_offer

alter table "alias_offer"
	add constraint ALIAS_OFFER_ALIAS_ID_FK
		foreign key ("id") references "alias" ("id")
			on delete cascade;

alter table "alias_offer"
	add constraint ALIAS_OFFER_ACCOUNT_ID_FK
		foreign key ("buyer_id") references "account" ("id")
			on delete cascade;

-- ask_order

alter table "ask_order"
	add constraint ASK_ORDER_ACCOUNT_ID_FK
		foreign key ("account_id") references "account" ("id")
			on delete cascade;

alter table "ask_order"
	add constraint ASK_ORDER_ASSET_ID_FK
		foreign key ("asset_id") references "asset" ("id")
			on delete cascade;

-- asset

alter table "asset"
	add constraint ASSET_ACCOUNT_ID_FK
		foreign key ("account_id") references "account" ("id")
			on delete cascade;

-- asset_transfer

alter table "asset_transfer"
	add constraint ASSET_TRANSFER_ACCOUNT_ID_FK
		foreign key ("sender_id") references "account" ("id")
			on delete cascade;

alter table "asset_transfer"
	add constraint ASSET_TRANSFER_ACCOUNT_ID_FK_2
		foreign key ("recipient_id") references "account" ("id")
			on delete cascade;

alter table "asset_transfer"
	add constraint ASSET_TRANSFER_ASSET_ID_FK
		foreign key ("asset_id") references "asset" ("id")
			on delete cascade;

-- at

alter table "at"
	add constraint AT_ACCOUNT_ID_FK
		foreign key ("creator_id") references "account" ("id")
			on delete cascade;

-- at_state

alter table "at_state"
	add constraint AT_STATE_AT_ID_FK
		foreign key ("at_id") references "at" ("id")
			on delete cascade;

-- bid_order

alter table "bid_order"
	add constraint BID_ORDER_ACCOUNT_ID_FK
		foreign key ("account_id") references "account" ("id")
			on delete cascade;

alter table "bid_order"
	add constraint BID_ORDER_ASSET_ID_FK
		foreign key ("asset_id") references "asset" ("id")
			on delete cascade;

-- block
-- no generator_id foreign key because account may be not yet created

-- escrow

alter table "escrow"
	add constraint ESCROW_ACCOUNT_ID_FK
		foreign key ("sender_id") references "account" ("id")
			on delete cascade;

alter table "escrow"
	add constraint ESCROW_ACCOUNT_ID_FK_2
		foreign key ("recipient_id") references "account" ("id")
			on delete cascade;

-- escrow_decision

alter table "escrow_decision"
	add constraint ESCROW_DECISION_ACCOUNT_ID_FK
		foreign key ("account_id") references "account" ("id")
			on delete cascade;

alter table "escrow_decision"
	add constraint ESCROW_DECISION_ESCROW_ID_FK
		foreign key ("escrow_id") references "escrow" ("id")
			on delete cascade;

-- goods

alter table "goods"
	add constraint GOODS_ACCOUNT_ID_FK
		foreign key ("seller_id") references "account" ("id")
			on delete cascade;

-- indirect_incoming

alter table "indirect_incoming"
	add constraint INDIRECT_INCOMING_ACCOUNT_ID_FK
		foreign key ("account_id") references "account" ("id")
			on delete cascade;

alter table "indirect_incoming"
	add constraint INDIRECT_INCOMING_TRANSACTION_ID_FK
		foreign key ("transaction_id") references "transaction" ("id")
			on delete cascade;

-- purchase

alter table "purchase"
	add constraint PURCHASE_TRANSACTION_ID_FK
		foreign key ("id") references "transaction" ("id")
			on delete cascade;

alter table "purchase"
	add constraint PURCHASE_ACCOUNT_ID_FK
		foreign key ("buyer_id") references "account" ("id")
			on delete cascade;

alter table "purchase"
	add constraint PURCHASE_ACCOUNT_ID_FK_2
		foreign key ("seller_id") references "account" ("id")
			on delete cascade;

alter table "purchase"
	add constraint PURCHASE_GOODS_ID_FK
		foreign key ("goods_id") references "goods" ("id")
			on delete cascade;

-- purchase_feedback

alter table "purchase_feedback"
	add constraint PURCHASE_FEEDBACK_PURCHASE_ID_FK
		foreign key ("id") references "purchase" ("id")
			on delete cascade;

-- purchase_public_feedback

alter table "purchase_public_feedback"
	add constraint PURCHASE_PUBLIC_FEEDBACK_PURCHASE_ID_FK
		foreign key ("id") references "purchase" ("id")
			on delete cascade;

-- reward_recip_assign

alter table "reward_recip_assign"
	add constraint REWARD_RECIP_ASSIGN_ACCOUNT_ID_FK
		foreign key ("account_id") references "account" ("id")
			on delete cascade;

alter table "reward_recip_assign"
	add constraint REWARD_RECIP_ASSIGN_ACCOUNT_ID_FK_2
		foreign key ("prev_recip_id") references "account" ("id")
			on delete cascade;

alter table "reward_recip_assign"
	add constraint REWARD_RECIP_ASSIGN_ACCOUNT_ID_FK_3
		foreign key ("recip_id") references "account" ("id")
			on delete cascade;

-- subscription

alter table "subscription"
	add constraint SUBSCRIPTION_ACCOUNT_ID_FK
		foreign key ("sender_id") references "account" ("id")
			on delete cascade;

alter table "subscription"
	add constraint SUBSCRIPTION_ACCOUNT_ID_FK_2
		foreign key ("recipient_id") references "account" ("id")
			on delete cascade;

-- trade

alter table "trade"
	add constraint TRADE_ACCOUNT_ID_FK
		foreign key ("seller_id") references "account" ("id")
			on delete cascade;

alter table "trade"
	add constraint TRADE_ACCOUNT_ID_FK_2
		foreign key ("buyer_id") references "account" ("id")
			on delete cascade;

alter table "trade"
	add constraint TRADE_ASK_ORDER_ID_FK
		foreign key ("ask_order_id") references "ask_order" ("id")
			on delete cascade;

alter table "trade"
	add constraint TRADE_ASSET_ID_FK
		foreign key ("asset_id") references "asset" ("id")
			on delete cascade;

alter table "trade"
	add constraint TRADE_BID_ORDER_ID_FK
		foreign key ("bid_order_id") references "bid_order" ("id")
			on delete cascade;

alter table "trade"
	add constraint TRADE_BLOCK_ID_FK
		foreign key ("block_id") references "block" ("id")
			on delete cascade;

-- transaction

alter table "transaction"
	add constraint TRANSACTION_ACCOUNT_ID_FK
		foreign key ("recipient_id") references "account" ("id")
			on delete cascade;

alter table "transaction"
	add constraint TRANSACTION_ACCOUNT_ID_FK_2
		foreign key ("sender_id") references "account" ("id")
			on delete cascade;

alter table "transaction"
	add constraint TRANSACTION_BLOCK_ID_FK
		foreign key ("block_id") references "block" ("id")
			on delete cascade;

alter table "transaction"
	add constraint TRANSACTION_BLOCK_ID_FK_2
		foreign key ("ec_block_id") references "block" ("id")
			on delete cascade;
