CREATE TABLE mpesa_transactions (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  user_id UUID NOT NULL REFERENCES users(id),
  idempotency_key VARCHAR(120) NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  sale_hash VARCHAR(64) NOT NULL,
  checkout_payload JSONB NOT NULL,
  provider_account VARCHAR(64) NOT NULL,
  phone_ciphertext TEXT NOT NULL,
  phone_hash VARCHAR(64) NOT NULL,
  phone_mask VARCHAR(20) NOT NULL,
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0 AND amount = trunc(amount)),
  status VARCHAR(32) NOT NULL CHECK (status IN
    ('QUEUED','SENDING','PENDING','UNKNOWN','PAID_UNCONFIRMED','SUCCEEDED','FAILED','REVIEW')),
  merchant_request_id VARCHAR(160),
  checkout_request_id VARCHAR(160) UNIQUE,
  receipt_number VARCHAR(40) UNIQUE,
  result_code INTEGER,
  result_description VARCHAR(300),
  sale_id UUID UNIQUE REFERENCES sales(id),
  paid_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  next_query_at TIMESTAMPTZ NOT NULL DEFAULT now() + INTERVAL '20 seconds',
  query_attempts INTEGER NOT NULL DEFAULT 0,
  CONSTRAINT ux_mpesa_idempotency UNIQUE (organization_id, idempotency_key)
);

-- A different browser key must not start another charge while this payer is unresolved.
CREATE UNIQUE INDEX ux_mpesa_unresolved_phone ON mpesa_transactions (organization_id, phone_hash)
  WHERE sale_id IS NULL AND status <> 'FAILED';
CREATE INDEX ix_mpesa_dispatch ON mpesa_transactions (created_at) WHERE status = 'QUEUED';
CREATE INDEX ix_mpesa_reconcile ON mpesa_transactions (next_query_at)
  WHERE status IN ('PENDING','UNKNOWN','PAID_UNCONFIRMED');
CREATE INDEX ix_mpesa_history ON mpesa_transactions (organization_id, branch_id, created_at DESC);

CREATE TABLE mpesa_callback_events (
  id UUID PRIMARY KEY,
  transaction_id UUID NOT NULL REFERENCES mpesa_transactions(id),
  payload_hash VARCHAR(64) NOT NULL,
  result_code INTEGER NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ux_mpesa_callback_event UNIQUE (transaction_id, payload_hash)
);
