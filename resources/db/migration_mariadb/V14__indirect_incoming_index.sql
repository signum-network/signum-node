-- Covered by the unique index indirect_incoming_db_id_uindex on (account_id, transaction_id).
-- MariaDB has no partial indexes, so the account_balance indexes stay as they are.
DROP INDEX IF EXISTS indirect_incoming_id_index ON indirect_incoming;
