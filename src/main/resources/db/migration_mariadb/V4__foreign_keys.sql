-- Descending index will give effect only for MySQL/Oracle, maybe for MariaDB in future
-- https://jira.mariadb.org/browse/MDEV-13756

-- account

drop index account_id_balance_height_idx on account;
create index account_id_balance_height_idx
	on account (id asc, balance asc, height desc);

drop index account_id_height_idx on account;
create unique index account_id_height_idx
	on account (id asc, height desc);

drop index account_asset_id_height_idx on account_asset;
create unique index account_asset_id_height_idx
	on account_asset (account_id asc, asset_id asc, height desc);

drop index account_asset_quantity_idx on account_asset;
create index account_asset_quantity_idx
	on account_asset (quantity desc);

-- account_asset

alter table account_asset
	add constraint account_asset_account_id_fk
		foreign key (account_id) references account (id)
			on delete cascade;

alter table account_asset
	add constraint account_asset_asset_id_fk
		foreign key (asset_id) references asset (id)
			on delete cascade;

-- alias

drop index alias_account_id_idx on alias;
create index alias_account_id_idx
	on alias (account_id asc, height desc);

drop index alias_id_height_idx on alias;
create unique index alias_id_height_idx
	on alias (id asc, height desc);

alter table alias
	add constraint alias_account_id_fk
		foreign key (account_id) references account (id)
			on delete cascade;

-- alias_offer

drop index alias_offer_id_height_idx on alias_offer;
create unique index alias_offer_id_height_idx
	on alias_offer (id asc, height desc);

alter table alias_offer
	add constraint alias_offer_account_id_fk
		foreign key (buyer_id) references account (id)
			on delete cascade;

-- ask_order

drop index ask_order_account_id_idx on ask_order;
create index ask_order_account_id_idx
	on ask_order (account_id asc, height desc);

drop index ask_order_creation_idx on ask_order;
create index ask_order_creation_idx
	on ask_order (creation_height desc);

drop index ask_order_id_height_idx on ask_order;
create unique index ask_order_id_height_idx
	on ask_order (id asc, height desc);

alter table ask_order
	add constraint ask_order_account_id_fk
		foreign key (account_id) references account (id)
			on delete cascade;

alter table ask_order
	add constraint ask_order_asset_id_fk
		foreign key (asset_id) references asset (id)
			on delete cascade;

-- asset

alter table asset
	add constraint asset_account_id_fk
		foreign key (account_id) references account (id)
			on delete cascade;

-- asset_transfer

drop index asset_transfer_asset_id_idx on asset_transfer;
create index asset_transfer_asset_id_idx
	on asset_transfer (asset_id asc, height desc);

drop index asset_transfer_recipient_id_idx on asset_transfer;
create index asset_transfer_recipient_id_idx
	on asset_transfer (recipient_id asc, height desc);

drop index asset_transfer_sender_id_idx on asset_transfer;
create index asset_transfer_sender_id_idx
	on asset_transfer (sender_id asc, height desc);

alter table asset_transfer
	add constraint asset_transfer_account_id_fk
		foreign key (sender_id) references account (id)
			on delete cascade;

alter table asset_transfer
	add constraint asset_transfer_account_id_fk_2
		foreign key (recipient_id) references account (id)
			on delete cascade;

alter table asset_transfer
	add constraint asset_transfer_asset_id_fk
		foreign key (asset_id) references asset (id)
			on delete cascade;

-- at

drop index at_creator_id_height_idx on at;
create index at_creator_id_height_idx
	on at (creator_id asc, height desc);

drop index at_id_height_idx on at;
create unique index at_id_height_idx
	on at (id asc, height desc);

alter table at
	add constraint at_account_id_fk
		foreign key (creator_id) references account (id)
			on delete cascade;

-- at_state

drop index at_state_at_id_height_idx on at_state;
create unique index at_state_at_id_height_idx
	on at_state (at_id asc, height desc);

drop index at_state_id_next_height_height_idx on at_state;
create index at_state_id_next_height_height_idx
	on at_state (at_id asc, next_height asc, height desc);

alter table at_state
	add constraint at_state_at_id_fk
		foreign key (at_id) references at (id)
			on delete cascade;

-- bid_order

drop index bid_order_account_id_idx on bid_order;
create index bid_order_account_id_idx
	on bid_order (account_id asc, height desc);

drop index bid_order_creation_idx on bid_order;
create index bid_order_creation_idx
	on bid_order (creation_height desc);

drop index bid_order_id_height_idx on bid_order;
create unique index bid_order_id_height_idx
	on bid_order (id asc, height desc);

alter table bid_order
	add constraint bid_order_account_id_fk
		foreign key (account_id) references account (id)
			on delete cascade;

alter table bid_order
	add constraint bid_order_asset_id_fk
		foreign key (asset_id) references asset (id)
			on delete cascade;

-- block
-- no generator_id foreign key because account may be not yet created

drop index block_timestamp_idx on block;
create unique index block_timestamp_idx
	on block (timestamp desc);

-- escrow

drop index escrow_deadline_height_idx on escrow;
create index escrow_deadline_height_idx
	on escrow (deadline asc, height desc);

drop index escrow_id_height_idx on escrow;
create unique index escrow_id_height_idx
	on escrow (id asc, height desc);

drop index escrow_recipient_id_height_idx on escrow;
create index escrow_recipient_id_height_idx
	on escrow (recipient_id asc, height desc);

drop index escrow_sender_id_height_idx on escrow;
create index escrow_sender_id_height_idx
	on escrow (sender_id asc, height desc);

alter table escrow
	add constraint escrow_account_id_fk
		foreign key (sender_id) references account (id)
			on delete cascade;

alter table escrow
	add constraint escrow_account_id_fk_2
		foreign key (recipient_id) references account (id)
			on delete cascade;

-- escrow_decision

drop index escrow_decision_account_id_height_idx on escrow_decision;
create index escrow_decision_account_id_height_idx
	on escrow_decision (account_id asc, height desc);

drop index escrow_decision_escrow_id_account_id_height_idx on escrow_decision;
create unique index escrow_decision_escrow_id_account_id_height_idx
	on escrow_decision (escrow_id asc, account_id asc, height desc);

drop index escrow_decision_escrow_id_height_idx on escrow_decision;
create index escrow_decision_escrow_id_height_idx
	on escrow_decision (escrow_id asc, height desc);

alter table escrow_decision
	add constraint escrow_decision_account_id_fk
		foreign key (account_id) references account (id)
			on delete cascade;

alter table escrow_decision
	add constraint escrow_decision_escrow_id_fk
		foreign key (escrow_id) references escrow (id)
			on delete cascade;

-- goods

drop index goods_id_height_idx on goods;
create unique index goods_id_height_idx
	on goods (id asc, height desc);

drop index goods_timestamp_idx on goods;
create index goods_timestamp_idx
	on goods (timestamp desc, height desc);

alter table goods
	add constraint goods_account_id_fk
		foreign key (seller_id) references account (id)
			on delete cascade;

-- indirect_incoming

alter table indirect_incoming
	add constraint indirect_incoming_account_id_fk
		foreign key (account_id) references account (id)
			on delete cascade;

alter table indirect_incoming
	add constraint indirect_incoming_transaction_id_fk
		foreign key (transaction_id) references transaction (id)
			on delete cascade;

-- purchase

drop index purchase_buyer_id_height_idx on purchase;
create index purchase_buyer_id_height_idx
	on purchase (buyer_id asc, height desc);

drop index purchase_deadline_idx on purchase;
create index purchase_deadline_idx
	on purchase (deadline asc, height desc);

drop index purchase_id_height_idx on purchase;
create unique index purchase_id_height_idx
	on purchase (id asc, height desc);

drop index purchase_seller_id_height_idx on purchase;
create index purchase_seller_id_height_idx
	on purchase (seller_id asc, height desc);

drop index purchase_timestamp_idx on purchase;
create index purchase_timestamp_idx
	on purchase (timestamp desc, id asc);

alter table purchase
	add constraint purchase_account_id_fk
		foreign key (buyer_id) references account (id)
			on delete cascade;

alter table purchase
	add constraint purchase_account_id_fk_2
		foreign key (seller_id) references account (id)
			on delete cascade;

alter table purchase
	add constraint purchase_goods_id_fk
		foreign key (goods_id) references goods (id)
			on delete cascade;

-- purchase_feedback
-- TODO: what is purchase_feedback.id? purchase.id?

drop index purchase_feedback_id_height_idx on purchase_feedback;
create index purchase_feedback_id_height_idx
	on purchase_feedback (id asc, height desc);

-- purchase_public_feedback
-- TODO: what is purchase_public_feedback.id? purchase.id?

drop index purchase_public_feedback_id_height_idx on purchase_public_feedback;
create index purchase_public_feedback_id_height_idx
	on purchase_public_feedback (id asc, height desc);

-- reward_recip_assign

drop index reward_recip_assign_account_id_height_idx on reward_recip_assign;
create unique index reward_recip_assign_account_id_height_idx
	on reward_recip_assign (account_id asc, height desc);

drop index reward_recip_assign_recip_id_height_idx on reward_recip_assign;
create index reward_recip_assign_recip_id_height_idx
	on reward_recip_assign (recip_id asc, height desc);

alter table reward_recip_assign
	add constraint reward_recip_assign_account_id_fk
		foreign key (account_id) references account (id)
			on delete cascade;

alter table reward_recip_assign
	add constraint reward_recip_assign_account_id_fk_2
		foreign key (prev_recip_id) references account (id)
			on delete cascade;

alter table reward_recip_assign
	add constraint reward_recip_assign_account_id_fk_3
		foreign key (recip_id) references account (id)
			on delete cascade;

-- subscription

drop index subscription_id_height_idx on subscription;
create unique index subscription_id_height_idx
	on subscription (id asc, height desc);

drop index subscription_recipient_id_height_idx on subscription;
create index subscription_recipient_id_height_idx
	on subscription (recipient_id asc, height desc);

drop index subscription_sender_id_height_idx on subscription;
create index subscription_sender_id_height_idx
	on subscription (sender_id asc, height desc);

alter table subscription
	add constraint subscription_account_id_fk
		foreign key (sender_id) references account (id)
			on delete cascade;

alter table subscription
	add constraint subscription_account_id_fk_2
		foreign key (recipient_id) references account (id)
			on delete cascade;

-- trade

drop index trade_asset_id_idx on trade;
create index trade_asset_id_idx
	on trade (asset_id asc, height desc);

drop index trade_buyer_id_idx on trade;
create index trade_buyer_id_idx
	on trade (buyer_id asc, height desc);

drop index trade_seller_id_idx on trade;
create index trade_seller_id_idx
	on trade (seller_id asc, height desc);

	alter table trade
	add constraint trade_account_id_fk
		foreign key (seller_id) references account (id)
			on delete cascade;

alter table trade
	add constraint trade_account_id_fk_2
		foreign key (buyer_id) references account (id)
			on delete cascade;

alter table trade
	add constraint trade_ask_order_id_fk
		foreign key (ask_order_id) references ask_order (id)
			on delete cascade;

alter table trade
	add constraint trade_asset_id_fk
		foreign key (asset_id) references asset (id)
			on delete cascade;

alter table trade
	add constraint trade_bid_order_id_fk
		foreign key (bid_order_id) references bid_order (id)
			on delete cascade;

alter table trade
	add constraint trade_block_id_fk
		foreign key (block_id) references block (id)
			on delete cascade;

-- transaction

drop index transaction_block_timestamp_idx on transaction;
create index transaction_block_timestamp_idx
	on transaction (block_timestamp desc);

alter table transaction
	add constraint transaction_account_id_fk
		foreign key (recipient_id) references account (id)
			on delete cascade;

alter table transaction
	add constraint transaction_account_id_fk_2
		foreign key (sender_id) references account (id)
			on delete cascade;

alter table transaction
	add constraint transaction_block_id_fk
		foreign key (block_id) references block (id)
			on delete cascade;

alter table transaction
	add constraint transaction_block_id_fk_2
		foreign key (ec_block_id) references block (id)
			on delete cascade;
