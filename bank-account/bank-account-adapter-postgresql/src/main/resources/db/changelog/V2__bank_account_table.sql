SET search_path TO "bank-account";

CREATE TABLE IF NOT EXISTS bank_account (
    id               BIGSERIAL    PRIMARY KEY,
    aggregate_id     UUID         NOT NULL UNIQUE,
    email            VARCHAR(255) NOT NULL UNIQUE,
    amount           NUMERIC(19, 4) NOT NULL,
    opened_transactions  JSONB    NOT NULL DEFAULT '[]',
    finished_transactions JSONB   NOT NULL DEFAULT '[]',
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bank_account_aggregate_id ON bank_account (aggregate_id);
