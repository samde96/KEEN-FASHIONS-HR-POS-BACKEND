CREATE TABLE employee_documents (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  employee_id UUID NOT NULL REFERENCES employees(id),
  uploaded_by_user_id UUID REFERENCES users(id),
  document_type VARCHAR(80) NOT NULL,
  title VARCHAR(160) NOT NULL,
  file_name VARCHAR(220) NOT NULL,
  content_type VARCHAR(120) NOT NULL,
  size_bytes BIGINT NOT NULL,
  notes VARCHAR(1000),
  file_data BYTEA NOT NULL,
  uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_employee_documents_organization_uploaded
  ON employee_documents (organization_id, uploaded_at DESC);

CREATE INDEX ix_employee_documents_employee
  ON employee_documents (employee_id, uploaded_at DESC);

CREATE INDEX ix_employee_documents_type
  ON employee_documents (organization_id, document_type);
