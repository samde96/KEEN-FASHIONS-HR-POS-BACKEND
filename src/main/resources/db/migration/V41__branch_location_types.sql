ALTER TABLE branches
  ADD COLUMN branch_type VARCHAR(16) NOT NULL DEFAULT 'SHOP';

ALTER TABLE branches
  ADD CONSTRAINT ck_branches_branch_type
  CHECK (branch_type IN ('STORE', 'SHOP', 'WAREHOUSE'));

CREATE INDEX ix_branches_organization_type_status
  ON branches (organization_id, branch_type, status);
