CREATE TABLE suppliers (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  name VARCHAR(160) NOT NULL,
  contact_person VARCHAR(160),
  phone VARCHAR(40),
  email VARCHAR(254),
  notes VARCHAR(500),
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_suppliers_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX ux_suppliers_organization_name
  ON suppliers (organization_id, LOWER(name));

CREATE INDEX ix_suppliers_organization_status
  ON suppliers (organization_id, status);
