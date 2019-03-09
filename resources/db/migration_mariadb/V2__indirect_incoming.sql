CREATE TABLE IF NOT EXISTS indirect_incoming
(
    db_id bigint AUTO_INCREMENT PRIMARY KEY NOT NULL,
    account_id bigint NOT NULL,
    transaction_id bigint NOT NULL
);
CREATE UNIQUE INDEX indirect_incoming_db_id_uindex ON indirect_incoming (db_id);