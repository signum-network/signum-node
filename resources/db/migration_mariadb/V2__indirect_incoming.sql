CREATE TABLE IF NOT EXISTS indirect_incoming
(
    db_id bigint(20) AUTO_INCREMENT,
    account_id bigint(20) NOT NULL,
    transaction_id bigint(20) NOT NULL,
    height INT NOT NULL,
    PRIMARY KEY (db_id)
);
CREATE UNIQUE INDEX indirect_incoming_db_id_uindex ON indirect_incoming (account_id, transaction_id);