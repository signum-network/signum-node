CREATE INDEX IF NOT EXISTS transaction_recipient_id_sender_id_idx ON "transaction" (recipient_id, sender_id);
