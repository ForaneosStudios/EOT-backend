-- Opt-in idempotency for API mutations. A client that sends the
-- Idempotency-Key header gets the stored response when it retries the same
-- request, instead of running it twice. Requests without the header are
-- unaffected, so existing clients (mobile app, game) keep their behaviour.
CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_sub VARCHAR(64) NOT NULL,
    idem_key VARCHAR(128) NOT NULL,
    request_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(512) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(12) NOT NULL,
    response_status INT,
    response_body TEXT,
    response_content_type VARCHAR(255),
    location_header VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_idempotency_keys_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT uq_idempotency_keys_owner_key UNIQUE (auth_sub, idem_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires_at ON idempotency_keys(expires_at);

ALTER TABLE public.idempotency_keys ENABLE ROW LEVEL SECURITY;
REVOKE ALL PRIVILEGES ON TABLE public.idempotency_keys FROM anon, authenticated;
