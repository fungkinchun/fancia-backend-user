-- Billing / invoice history for Premium (and future) payments.
CREATE TABLE IF NOT EXISTS payment_transactions (
    id uuid NOT NULL,
    created_by uuid,
    created_at timestamp(6) without time zone,
    deleted boolean NOT NULL DEFAULT false,
    user_id uuid NOT NULL,
    provider varchar(16) NOT NULL,
    provider_transaction_id varchar(512) NOT NULL,
    provider_subscription_id varchar(512),
    amount_cents bigint NOT NULL,
    currency varchar(8) NOT NULL,
    status varchar(32) NOT NULL,
    description varchar(512),
    invoice_url varchar(1024),
    paid_at timestamp without time zone,
    raw_payload text,
    updated_at timestamp without time zone,
    CONSTRAINT payment_transactions_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_transactions_provider_tx_id
    ON payment_transactions (provider, provider_transaction_id)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_payment_transactions_user_id
    ON payment_transactions (user_id)
    WHERE deleted = false;
