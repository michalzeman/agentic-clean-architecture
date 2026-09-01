ALTER TABLE bank_transaction
    ADD COLUMN IF NOT EXISTS aggregate_version BIGINT;

UPDATE bank_transaction SET aggregate_version = version;

ALTER TABLE bank_transaction
    ALTER COLUMN aggregate_version SET NOT NULL;
