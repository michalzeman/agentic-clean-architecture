CREATE TABLE IF NOT EXISTS bank_account (
    id               BIGSERIAL    PRIMARY KEY,
    aggregate_id     UUID         NOT NULL UNIQUE,
    email            VARCHAR(255) NOT NULL UNIQUE,
    amount           NUMERIC(19, 4) NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bank_account_aggregate_id ON bank_account (aggregate_id);

CREATE TABLE IF NOT EXISTS bank_account_transaction_ref (
    id              BIGSERIAL    PRIMARY KEY,
    bank_account_id BIGINT       NOT NULL,
    transaction_id  VARCHAR(255) NOT NULL,
    state           VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_bank_account_transaction_ref_account
        FOREIGN KEY (bank_account_id)
            REFERENCES bank_account (id)
            ON DELETE CASCADE,
    CONSTRAINT chk_bank_account_transaction_ref_state
        CHECK (state IN ('OPENED', 'FINISHED')),
    CONSTRAINT uk_bank_account_transaction_ref_unique
        UNIQUE (bank_account_id, transaction_id, state)
);

CREATE INDEX IF NOT EXISTS idx_bank_account_transaction_ref_account_state
    ON bank_account_transaction_ref (bank_account_id, state);

CREATE INDEX IF NOT EXISTS idx_bank_account_transaction_ref_transaction_id
    ON bank_account_transaction_ref (transaction_id);
