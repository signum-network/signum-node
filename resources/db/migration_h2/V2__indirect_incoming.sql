CREATE TABLE IF NOT EXISTS indirect_incoming
(
    db_id IDENTITY,
    account_id bigint NOT NULL,
    transaction_id bigint NOT NULL,
    height INT NOT NULL
);
CREATE UNIQUE INDEX indirect_incoming_db_id_uindex ON indirect_incoming (account_id, transaction_id);