ALTER TABLE bank_account
    ADD COLUMN IF NOT EXISTS aggregate_version BIGINT;

UPDATE bank_account SET aggregate_version = version;

ALTER TABLE bank_account
    ALTER COLUMN aggregate_version SET NOT NULL;
