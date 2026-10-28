ALTER TABLE users
ADD COLUMN user_role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
ADD CONSTRAINT chk_users_role CHECK (
    user_role IN ('CUSTOMER', 'ADMIN')
);

CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    account_number VARCHAR(29) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'UAH',
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_accounts_number UNIQUE (account_number),
    CONSTRAINT chk_accounts_currency CHECK (
        currency IN ('UAH', 'USD', 'EUR')
    ),
    CONSTRAINT chk_accounts_balance CHECK (balance >= 0),
    CONSTRAINT chk_accounts_status CHECK (
        status IN ('ACTIVE', 'BLOCKED')
    )
);

CREATE INDEX idx_accounts_user ON accounts (user_id);

CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    from_account_id BIGINT REFERENCES accounts (id),
    to_account_id BIGINT REFERENCES accounts (id),
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    failure_reason VARCHAR(255),
    description VARCHAR(255),
    initiated_by BIGINT NOT NULL REFERENCES users (id),
    idempotency_key VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_transactions_amount CHECK (amount > 0),
    CONSTRAINT chk_transactions_currency CHECK (
        currency IN ('UAH', 'USD', 'EUR')
    ),
    CONSTRAINT chk_transactions_type CHECK (
        transaction_type IN (
            'DEPOSIT',
            'WITHDRAWAL',
            'TRANSFER'
        )
    ),
    CONSTRAINT chk_transactions_status CHECK (
        status IN ('SUCCESS', 'FAILED')
    ),
    CONSTRAINT chk_transactions_failure_reason CHECK (
        status = 'FAILED'
        OR failure_reason IS NULL
    ),
    CONSTRAINT chk_transaction_accounts CHECK (
        (
            transaction_type = 'DEPOSIT'
            AND from_account_id IS NULL
            AND to_account_id IS NOT NULL
        )
        OR (
            transaction_type = 'WITHDRAWAL'
            AND from_account_id IS NOT NULL
            AND to_account_id IS NULL
        )
        OR (
            transaction_type = 'TRANSFER'
            AND from_account_id IS NOT NULL
            AND to_account_id IS NOT NULL
            AND from_account_id <> to_account_id
        )
    ),
    CONSTRAINT uq_transactions_idempotency UNIQUE (initiated_by, idempotency_key)
);

CREATE INDEX idx_transactions_from_created ON transactions (
    from_account_id,
    created_at DESC
);

CREATE INDEX idx_transactions_to_created ON transactions (
    to_account_id,
    created_at DESC
);

CREATE INDEX idx_transactions_created ON transactions (created_at DESC);