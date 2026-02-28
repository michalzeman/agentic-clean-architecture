SET search_path TO "bank-transaction";

CREATE TABLE IF NOT EXISTS bank_transaction (
    id               BIGSERIAL    PRIMARY KEY,
    aggregate_id     UUID         NOT NULL UNIQUE,
    correlation_id   VARCHAR(255) NOT NULL,
    from_account_id  UUID         NOT NULL,
    to_account_id    UUID         NOT NULL,
    amount           NUMERIC(19, 4) NOT NULL,
    money_withdrawn  BOOLEAN      NOT NULL DEFAULT FALSE,
    money_deposited  BOOLEAN      NOT NULL DEFAULT FALSE,
    status           VARCHAR(50)  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bank_transaction_aggregate_id ON bank_transaction (aggregate_id);

CREATE TABLE IF NOT EXISTS account_view (
    id         BIGSERIAL PRIMARY KEY,
    account_id UUID      NOT NULL UNIQUE
);

CREATE INDEX IF NOT EXISTS idx_account_view_account_id ON account_view (account_id);
