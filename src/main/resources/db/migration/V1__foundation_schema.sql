CREATE TABLE organizations (
  id UUID PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  currency_code CHAR(3) NOT NULL,
  time_zone VARCHAR(64) NOT NULL,
  tax_registration_number VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_organizations_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
  CONSTRAINT ck_organizations_currency_upper CHECK (currency_code = UPPER(currency_code))
);

CREATE TABLE branches (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  name VARCHAR(160) NOT NULL,
  code VARCHAR(24) NOT NULL,
  time_zone VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_branches_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT ux_branches_organization_code UNIQUE (organization_id, code)
);

CREATE TABLE registers (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  name VARCHAR(120) NOT NULL,
  code VARCHAR(24) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_registers_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT ux_registers_branch_code UNIQUE (branch_id, code)
);

CREATE TABLE users (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  email VARCHAR(254) NOT NULL,
  display_name VARCHAR(160) NOT NULL,
  password_hash VARCHAR(255),
  status VARCHAR(32) NOT NULL,
  last_login_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX ux_users_organization_email
ON users (organization_id, LOWER(email));

CREATE TABLE roles (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  name VARCHAR(80) NOT NULL,
  description VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_roles_organization_name UNIQUE (organization_id, name)
);

CREATE TABLE permissions (
  id UUID PRIMARY KEY,
  code VARCHAR(120) NOT NULL,
  description VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ux_permissions_code UNIQUE (code)
);

CREATE TABLE role_permissions (
  role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
  PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE user_branch_assignments (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  branch_id UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ux_user_branch_assignments_user_branch
    UNIQUE (user_id, branch_id)
);

CREATE TABLE audit_events (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID REFERENCES branches(id),
  actor_user_id UUID REFERENCES users(id),
  action VARCHAR(120) NOT NULL,
  target_type VARCHAR(120) NOT NULL,
  target_id UUID,
  reason VARCHAR(500),
  correlation_id VARCHAR(80),
  metadata_json JSON NOT NULL DEFAULT '{}',
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE idempotency_keys (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  operation VARCHAR(120) NOT NULL,
  idempotency_key VARCHAR(120) NOT NULL,
  request_hash VARCHAR(128) NOT NULL,
  response_reference VARCHAR(255),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ux_idempotency_operation_key
    UNIQUE (organization_id, operation, idempotency_key)
);

CREATE INDEX ix_branches_organization_status
ON branches (organization_id, status);

CREATE INDEX ix_users_organization_status
ON users (organization_id, status);

CREATE INDEX ix_user_branch_assignments_user
ON user_branch_assignments (user_id);

CREATE INDEX ix_audit_events_organization_created
ON audit_events (organization_id, created_at DESC);

CREATE INDEX ix_idempotency_organization_created
ON idempotency_keys (organization_id, created_at DESC);
