-- Premium entitlement columns on users (source of truth for clients via user-service).
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS premium_active boolean NOT NULL DEFAULT false;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS premium_expires_at timestamp without time zone;

-- Unified subscription records owned by payment-service.
CREATE TABLE IF NOT EXISTS subscriptions (
    id uuid NOT NULL,
    created_by uuid,
    created_at timestamp(6) without time zone,
    deleted boolean NOT NULL DEFAULT false,
    user_id uuid NOT NULL,
    provider varchar(16) NOT NULL,
    provider_subscription_id varchar(512) NOT NULL,
    product_id varchar(255),
    status varchar(32) NOT NULL,
    expires_at timestamp without time zone,
    environment varchar(32),
    raw_payload text,
    updated_at timestamp without time zone,
    CONSTRAINT subscriptions_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_subscriptions_provider_subscription_id
    ON subscriptions (provider, provider_subscription_id)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_id
    ON subscriptions (user_id)
    WHERE deleted = false;

-- Idempotency log for provider webhook deliveries.
CREATE TABLE IF NOT EXISTS webhook_events (
    id uuid NOT NULL,
    created_by uuid,
    created_at timestamp(6) without time zone,
    deleted boolean NOT NULL DEFAULT false,
    provider varchar(16) NOT NULL,
    event_id varchar(512) NOT NULL,
    event_type varchar(128),
    processed_at timestamp without time zone,
    raw_payload text,
    CONSTRAINT webhook_events_pkey PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_webhook_events_provider_event_id
    ON webhook_events (provider, event_id)
    WHERE deleted = false;
