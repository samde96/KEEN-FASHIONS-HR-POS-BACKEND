ALTER TABLE branches
  DROP CONSTRAINT ck_branches_branch_type;

ALTER TABLE branches
  ADD CONSTRAINT ck_branches_branch_type
  CHECK (branch_type IN ('STORE', 'SHOP', 'WAREHOUSE', 'HOTEL'));
