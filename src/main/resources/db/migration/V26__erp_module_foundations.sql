ALTER TABLE suppliers
  ADD COLUMN supplier_code VARCHAR(40),
  ADD COLUMN company_name VARCHAR(160),
  ADD COLUMN address VARCHAR(255),
  ADD COLUMN kra_pin VARCHAR(40),
  ADD COLUMN payment_terms VARCHAR(120),
  ADD COLUMN credit_limit NUMERIC(14, 2) NOT NULL DEFAULT 0,
  ADD COLUMN bank_details VARCHAR(500),
  ADD COLUMN products_supplied VARCHAR(500),
  ADD COLUMN outstanding_balance NUMERIC(14, 2) NOT NULL DEFAULT 0;

UPDATE suppliers
SET company_name = name
WHERE company_name IS NULL;

ALTER TABLE suppliers
  ALTER COLUMN company_name SET NOT NULL,
  ADD CONSTRAINT ck_suppliers_credit_non_negative CHECK (credit_limit >= 0),
  ADD CONSTRAINT ck_suppliers_outstanding_non_negative CHECK (outstanding_balance >= 0);

CREATE UNIQUE INDEX ux_suppliers_organization_supplier_code
  ON suppliers (organization_id, supplier_code);

ALTER TABLE products
  ADD COLUMN brand VARCHAR(80),
  ADD COLUMN unit_of_measure VARCHAR(40) NOT NULL DEFAULT 'PCS',
  ADD COLUMN etims_item_code VARCHAR(80),
  ADD COLUMN kra_classification VARCHAR(120),
  ADD COLUMN tax_type VARCHAR(80),
  ADD COLUMN etims_unit_of_measure VARCHAR(40),
  ADD COLUMN etims_tax_rate NUMERIC(8, 4);

CREATE TABLE customers (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID REFERENCES branches(id),
  customer_code VARCHAR(40) NOT NULL,
  name VARCHAR(160) NOT NULL,
  contact_person VARCHAR(160),
  phone VARCHAR(40),
  email VARCHAR(254),
  address VARCHAR(255),
  kra_pin VARCHAR(40),
  customer_type VARCHAR(32) NOT NULL,
  credit_limit NUMERIC(14, 2) NOT NULL DEFAULT 0,
  outstanding_balance NUMERIC(14, 2) NOT NULL DEFAULT 0,
  payment_terms VARCHAR(120),
  notes VARCHAR(500),
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_customers_organization_code UNIQUE (organization_id, customer_code),
  CONSTRAINT ck_customers_type CHECK (customer_type IN ('INDIVIDUAL', 'BUSINESS')),
  CONSTRAINT ck_customers_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'CREDIT_HOLD')),
  CONSTRAINT ck_customers_credit_non_negative CHECK (credit_limit >= 0),
  CONSTRAINT ck_customers_outstanding_non_negative CHECK (outstanding_balance >= 0)
);

CREATE INDEX ix_customers_organization_status
  ON customers (organization_id, status);

CREATE TABLE purchase_requests (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  requested_by_user_id UUID REFERENCES users(id),
  request_number VARCHAR(40) NOT NULL,
  required_date DATE,
  reason VARCHAR(500),
  status VARCHAR(32) NOT NULL,
  approved_by_user_id UUID REFERENCES users(id),
  approved_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_purchase_requests_organization_number UNIQUE (organization_id, request_number),
  CONSTRAINT ck_purchase_requests_status CHECK (
    status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED', 'CONVERTED')
  )
);

CREATE TABLE purchase_request_lines (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  purchase_request_id UUID NOT NULL REFERENCES purchase_requests(id) ON DELETE CASCADE,
  product_id UUID NOT NULL REFERENCES products(id),
  quantity NUMERIC(14, 3) NOT NULL,
  estimated_unit_cost NUMERIC(14, 2),
  notes VARCHAR(255),
  CONSTRAINT ck_purchase_request_lines_quantity_positive CHECK (quantity > 0),
  CONSTRAINT ck_purchase_request_lines_cost_non_negative CHECK (
    estimated_unit_cost IS NULL OR estimated_unit_cost >= 0
  )
);

CREATE TABLE rfqs (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  purchase_request_id UUID REFERENCES purchase_requests(id),
  rfq_number VARCHAR(40) NOT NULL,
  title VARCHAR(180) NOT NULL,
  status VARCHAR(32) NOT NULL,
  response_due_date DATE,
  notes VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_rfqs_organization_number UNIQUE (organization_id, rfq_number),
  CONSTRAINT ck_rfqs_status CHECK (
    status IN ('DRAFT', 'SENT', 'QUOTED', 'AWARDED', 'CANCELLED', 'CLOSED')
  )
);

CREATE TABLE rfq_lines (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  rfq_id UUID NOT NULL REFERENCES rfqs(id) ON DELETE CASCADE,
  product_id UUID NOT NULL REFERENCES products(id),
  quantity NUMERIC(14, 3) NOT NULL,
  notes VARCHAR(255),
  CONSTRAINT ck_rfq_lines_quantity_positive CHECK (quantity > 0)
);

CREATE TABLE rfq_supplier_quotes (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  rfq_id UUID NOT NULL REFERENCES rfqs(id) ON DELETE CASCADE,
  supplier_id UUID NOT NULL REFERENCES suppliers(id),
  unit_price NUMERIC(14, 2) NOT NULL,
  discount_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  tax_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  delivery_days INTEGER,
  payment_terms VARCHAR(120),
  status VARCHAR(32) NOT NULL,
  notes VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_rfq_supplier_quotes_amounts_non_negative CHECK (
    unit_price >= 0 AND discount_amount >= 0 AND tax_amount >= 0
  ),
  CONSTRAINT ck_rfq_supplier_quotes_status CHECK (
    status IN ('RECEIVED', 'SHORTLISTED', 'AWARDED', 'REJECTED')
  )
);

CREATE TABLE purchase_orders (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  supplier_id UUID NOT NULL REFERENCES suppliers(id),
  purchase_request_id UUID REFERENCES purchase_requests(id),
  rfq_id UUID REFERENCES rfqs(id),
  po_number VARCHAR(40) NOT NULL,
  expected_delivery DATE,
  payment_terms VARCHAR(120),
  subtotal_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  discount_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  tax_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  total_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  notes VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_purchase_orders_organization_number UNIQUE (organization_id, po_number),
  CONSTRAINT ck_purchase_orders_amounts_non_negative CHECK (
    subtotal_amount >= 0 AND discount_amount >= 0 AND tax_amount >= 0 AND total_amount >= 0
  ),
  CONSTRAINT ck_purchase_orders_status CHECK (
    status IN (
      'DRAFT',
      'PENDING_APPROVAL',
      'APPROVED',
      'SENT',
      'PARTIALLY_RECEIVED',
      'FULLY_RECEIVED',
      'CANCELLED',
      'CLOSED'
    )
  )
);

CREATE TABLE purchase_order_lines (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
  product_id UUID NOT NULL REFERENCES products(id),
  quantity NUMERIC(14, 3) NOT NULL,
  received_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
  unit_cost NUMERIC(14, 2) NOT NULL,
  discount_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  tax_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  line_total NUMERIC(14, 2) NOT NULL DEFAULT 0,
  CONSTRAINT ck_purchase_order_lines_quantities_non_negative CHECK (
    quantity > 0 AND received_quantity >= 0
  ),
  CONSTRAINT ck_purchase_order_lines_amounts_non_negative CHECK (
    unit_cost >= 0 AND discount_amount >= 0 AND tax_amount >= 0 AND line_total >= 0
  )
);

CREATE TABLE goods_receipts (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
  receipt_number VARCHAR(40) NOT NULL,
  received_by_user_id UUID REFERENCES users(id),
  status VARCHAR(32) NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  notes VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_goods_receipts_organization_number UNIQUE (organization_id, receipt_number),
  CONSTRAINT ck_goods_receipts_status CHECK (
    status IN ('DRAFT', 'POSTED', 'CANCELLED')
  )
);

CREATE TABLE goods_receipt_lines (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  goods_receipt_id UUID NOT NULL REFERENCES goods_receipts(id) ON DELETE CASCADE,
  product_id UUID NOT NULL REFERENCES products(id),
  received_quantity NUMERIC(14, 3) NOT NULL,
  damaged_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
  missing_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
  batch_number VARCHAR(80),
  expiry_date DATE,
  serial_number VARCHAR(120),
  unit_cost NUMERIC(14, 2),
  CONSTRAINT ck_goods_receipt_lines_quantities_non_negative CHECK (
    received_quantity >= 0 AND damaged_quantity >= 0 AND missing_quantity >= 0
  ),
  CONSTRAINT ck_goods_receipt_lines_cost_non_negative CHECK (unit_cost IS NULL OR unit_cost >= 0)
);

CREATE TABLE supplier_invoices (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  supplier_id UUID NOT NULL REFERENCES suppliers(id),
  purchase_order_id UUID REFERENCES purchase_orders(id),
  goods_receipt_id UUID REFERENCES goods_receipts(id),
  invoice_number VARCHAR(80) NOT NULL,
  subtotal_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  tax_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  total_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  paid_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  due_date DATE,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_supplier_invoices_supplier_number UNIQUE (supplier_id, invoice_number),
  CONSTRAINT ck_supplier_invoices_amounts_non_negative CHECK (
    subtotal_amount >= 0 AND tax_amount >= 0 AND total_amount >= 0 AND paid_amount >= 0
  ),
  CONSTRAINT ck_supplier_invoices_status CHECK (
    status IN ('DRAFT', 'PENDING_MATCH', 'APPROVED', 'PARTIALLY_PAID', 'PAID', 'VOID')
  )
);

CREATE TABLE crm_leads (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID REFERENCES branches(id),
  customer_id UUID REFERENCES customers(id),
  assigned_user_id UUID REFERENCES users(id),
  lead_number VARCHAR(40) NOT NULL,
  name VARCHAR(160) NOT NULL,
  company VARCHAR(160),
  phone VARCHAR(40),
  email VARCHAR(254),
  location VARCHAR(160),
  source VARCHAR(80),
  estimated_value NUMERIC(14, 2) NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  notes VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_crm_leads_organization_number UNIQUE (organization_id, lead_number),
  CONSTRAINT ck_crm_leads_status CHECK (
    status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'PROPOSAL', 'WON', 'LOST')
  ),
  CONSTRAINT ck_crm_leads_value_non_negative CHECK (estimated_value >= 0)
);

CREATE TABLE crm_opportunities (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID REFERENCES branches(id),
  lead_id UUID REFERENCES crm_leads(id),
  customer_id UUID REFERENCES customers(id),
  assigned_user_id UUID REFERENCES users(id),
  opportunity_number VARCHAR(40) NOT NULL,
  title VARCHAR(180) NOT NULL,
  expected_value NUMERIC(14, 2) NOT NULL DEFAULT 0,
  probability_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
  expected_close_date DATE,
  stage VARCHAR(32) NOT NULL,
  notes VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_crm_opportunities_organization_number UNIQUE (organization_id, opportunity_number),
  CONSTRAINT ck_crm_opportunities_stage CHECK (
    stage IN ('NEW', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST')
  ),
  CONSTRAINT ck_crm_opportunities_value_non_negative CHECK (expected_value >= 0),
  CONSTRAINT ck_crm_opportunities_probability CHECK (
    probability_percent >= 0 AND probability_percent <= 100
  )
);

CREATE TABLE crm_activities (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID REFERENCES branches(id),
  lead_id UUID REFERENCES crm_leads(id),
  opportunity_id UUID REFERENCES crm_opportunities(id),
  customer_id UUID REFERENCES customers(id),
  assigned_user_id UUID REFERENCES users(id),
  activity_type VARCHAR(32) NOT NULL,
  subject VARCHAR(180) NOT NULL,
  due_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  status VARCHAR(32) NOT NULL,
  notes VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_crm_activities_type CHECK (
    activity_type IN ('CALL', 'MEETING', 'TASK', 'FOLLOW_UP', 'NOTE')
  ),
  CONSTRAINT ck_crm_activities_status CHECK (
    status IN ('OPEN', 'DONE', 'CANCELLED')
  )
);

CREATE TABLE quotations (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  customer_id UUID REFERENCES customers(id),
  opportunity_id UUID REFERENCES crm_opportunities(id),
  salesperson_user_id UUID REFERENCES users(id),
  quotation_number VARCHAR(40) NOT NULL,
  expiry_date DATE,
  delivery_terms VARCHAR(255),
  payment_terms VARCHAR(120),
  subtotal_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  discount_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  tax_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  total_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  notes VARCHAR(500),
  terms_and_conditions VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_quotations_organization_number UNIQUE (organization_id, quotation_number),
  CONSTRAINT ck_quotations_amounts_non_negative CHECK (
    subtotal_amount >= 0 AND discount_amount >= 0 AND tax_amount >= 0 AND total_amount >= 0
  ),
  CONSTRAINT ck_quotations_status CHECK (
    status IN ('DRAFT', 'SENT', 'VIEWED', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED')
  )
);

CREATE TABLE quotation_lines (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  quotation_id UUID NOT NULL REFERENCES quotations(id) ON DELETE CASCADE,
  product_id UUID REFERENCES products(id),
  description VARCHAR(255) NOT NULL,
  quantity NUMERIC(14, 3) NOT NULL,
  unit_price NUMERIC(14, 2) NOT NULL,
  discount_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  tax_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  line_total NUMERIC(14, 2) NOT NULL DEFAULT 0,
  CONSTRAINT ck_quotation_lines_quantity_positive CHECK (quantity > 0),
  CONSTRAINT ck_quotation_lines_amounts_non_negative CHECK (
    unit_price >= 0 AND discount_amount >= 0 AND tax_amount >= 0 AND line_total >= 0
  )
);

CREATE TABLE sales_orders (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  customer_id UUID REFERENCES customers(id),
  quotation_id UUID REFERENCES quotations(id),
  salesperson_user_id UUID REFERENCES users(id),
  sales_order_number VARCHAR(40) NOT NULL,
  delivery_date DATE,
  delivery_address VARCHAR(255),
  payment_terms VARCHAR(120),
  subtotal_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  discount_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  tax_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  total_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  fulfilled_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  notes VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_sales_orders_organization_number UNIQUE (organization_id, sales_order_number),
  CONSTRAINT ck_sales_orders_amounts_non_negative CHECK (
    subtotal_amount >= 0
    AND discount_amount >= 0
    AND tax_amount >= 0
    AND total_amount >= 0
    AND fulfilled_amount >= 0
  ),
  CONSTRAINT ck_sales_orders_status CHECK (
    status IN ('DRAFT', 'CONFIRMED', 'PARTIALLY_FULFILLED', 'FULFILLED', 'INVOICED', 'CANCELLED')
  )
);

CREATE TABLE sales_order_lines (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  sales_order_id UUID NOT NULL REFERENCES sales_orders(id) ON DELETE CASCADE,
  product_id UUID REFERENCES products(id),
  description VARCHAR(255) NOT NULL,
  quantity NUMERIC(14, 3) NOT NULL,
  fulfilled_quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
  unit_price NUMERIC(14, 2) NOT NULL,
  discount_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  tax_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  line_total NUMERIC(14, 2) NOT NULL DEFAULT 0,
  CONSTRAINT ck_sales_order_lines_quantities_non_negative CHECK (
    quantity > 0 AND fulfilled_quantity >= 0
  ),
  CONSTRAINT ck_sales_order_lines_amounts_non_negative CHECK (
    unit_price >= 0 AND discount_amount >= 0 AND tax_amount >= 0 AND line_total >= 0
  )
);

CREATE TABLE invoices (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  customer_id UUID REFERENCES customers(id),
  sale_id UUID REFERENCES sales(id),
  sales_order_id UUID REFERENCES sales_orders(id),
  quotation_id UUID REFERENCES quotations(id),
  salesperson_user_id UUID REFERENCES users(id),
  invoice_number VARCHAR(40) NOT NULL,
  customer_kra_pin VARCHAR(40),
  subtotal_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  discount_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  tax_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  total_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  paid_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  due_date DATE,
  payment_terms VARCHAR(120),
  etims_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED',
  etims_reference VARCHAR(120),
  status VARCHAR(32) NOT NULL,
  notes VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_invoices_organization_number UNIQUE (organization_id, invoice_number),
  CONSTRAINT ck_invoices_amounts_non_negative CHECK (
    subtotal_amount >= 0
    AND discount_amount >= 0
    AND tax_amount >= 0
    AND total_amount >= 0
    AND paid_amount >= 0
  ),
  CONSTRAINT ck_invoices_status CHECK (
    status IN ('DRAFT', 'SENT', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED', 'VOIDED')
  ),
  CONSTRAINT ck_invoices_etims_status CHECK (
    etims_status IN (
      'NOT_SUBMITTED',
      'PENDING',
      'SUBMITTED',
      'ACCEPTED',
      'REJECTED',
      'FAILED',
      'RETRY_REQUIRED',
      'CANCELLED',
      'CREDITED'
    )
  )
);

CREATE TABLE invoice_lines (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  product_id UUID REFERENCES products(id),
  description VARCHAR(255) NOT NULL,
  quantity NUMERIC(14, 3) NOT NULL,
  unit_price NUMERIC(14, 2) NOT NULL,
  discount_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  tax_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  line_total NUMERIC(14, 2) NOT NULL DEFAULT 0,
  CONSTRAINT ck_invoice_lines_quantity_positive CHECK (quantity > 0),
  CONSTRAINT ck_invoice_lines_amounts_non_negative CHECK (
    unit_price >= 0 AND discount_amount >= 0 AND tax_amount >= 0 AND line_total >= 0
  )
);

CREATE TABLE financial_periods (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  name VARCHAR(120) NOT NULL,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_financial_periods_organization_name UNIQUE (organization_id, name),
  CONSTRAINT ck_financial_periods_dates CHECK (period_end >= period_start),
  CONSTRAINT ck_financial_periods_status CHECK (status IN ('OPEN', 'CLOSED', 'LOCKED'))
);

CREATE TABLE accounts (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  parent_account_id UUID REFERENCES accounts(id),
  account_code VARCHAR(40) NOT NULL,
  account_name VARCHAR(160) NOT NULL,
  account_type VARCHAR(32) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_accounts_organization_code UNIQUE (organization_id, account_code),
  CONSTRAINT ck_accounts_type CHECK (
    account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'COGS', 'EXPENSE')
  )
);

CREATE TABLE journal_entries (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID REFERENCES branches(id),
  financial_period_id UUID REFERENCES financial_periods(id),
  created_by_user_id UUID REFERENCES users(id),
  journal_number VARCHAR(40) NOT NULL,
  entry_date DATE NOT NULL,
  description VARCHAR(255) NOT NULL,
  reference VARCHAR(120),
  source_module VARCHAR(80),
  source_transaction_id UUID,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_journal_entries_organization_number UNIQUE (organization_id, journal_number),
  CONSTRAINT ck_journal_entries_status CHECK (
    status IN ('DRAFT', 'POSTED', 'REVERSED', 'VOID')
  )
);

CREATE TABLE journal_entry_lines (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  journal_entry_id UUID NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
  account_id UUID NOT NULL REFERENCES accounts(id),
  debit_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  credit_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
  description VARCHAR(255),
  CONSTRAINT ck_journal_entry_lines_amounts_non_negative CHECK (
    debit_amount >= 0 AND credit_amount >= 0
  ),
  CONSTRAINT ck_journal_entry_lines_one_side CHECK (
    (debit_amount > 0 AND credit_amount = 0) OR (credit_amount > 0 AND debit_amount = 0)
  )
);

CREATE TABLE tax_rules (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  tax_code VARCHAR(40) NOT NULL,
  name VARCHAR(120) NOT NULL,
  tax_category VARCHAR(40) NOT NULL,
  rate NUMERIC(8, 4) NOT NULL,
  inclusive BOOLEAN NOT NULL DEFAULT FALSE,
  effective_from DATE NOT NULL,
  effective_to DATE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_tax_rules_organization_code_from UNIQUE (organization_id, tax_code, effective_from),
  CONSTRAINT ck_tax_rules_rate_non_negative CHECK (rate >= 0),
  CONSTRAINT ck_tax_rules_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE etims_configurations (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID REFERENCES branches(id),
  kra_pin VARCHAR(40) NOT NULL,
  taxpayer_name VARCHAR(160) NOT NULL,
  environment VARCHAR(32) NOT NULL,
  integration_status VARCHAR(32) NOT NULL,
  submission_mode VARCHAR(32) NOT NULL,
  credentials_reference VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_etims_config_environment CHECK (environment IN ('SANDBOX', 'PRODUCTION')),
  CONSTRAINT ck_etims_config_status CHECK (
    integration_status IN ('NOT_CONFIGURED', 'CONFIGURED', 'CONNECTED', 'FAILED', 'DISABLED')
  ),
  CONSTRAINT ck_etims_config_submission_mode CHECK (
    submission_mode IN ('MANUAL', 'AUTOMATIC')
  )
);

CREATE TABLE etims_submission_queue (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID REFERENCES branches(id),
  invoice_id UUID REFERENCES invoices(id),
  sale_id UUID REFERENCES sales(id),
  source_type VARCHAR(40) NOT NULL,
  source_transaction_id UUID NOT NULL,
  idempotency_key VARCHAR(120) NOT NULL,
  status VARCHAR(32) NOT NULL,
  retry_count INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ,
  last_attempt_at TIMESTAMPTZ,
  submitted_at TIMESTAMPTZ,
  etims_reference VARCHAR(120),
  response_code VARCHAR(40),
  response_message VARCHAR(500),
  error_details VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ux_etims_submission_idempotency UNIQUE (organization_id, idempotency_key),
  CONSTRAINT ck_etims_submission_retry_non_negative CHECK (retry_count >= 0),
  CONSTRAINT ck_etims_submission_status CHECK (
    status IN (
      'NOT_SUBMITTED',
      'PENDING',
      'SUBMITTED',
      'ACCEPTED',
      'REJECTED',
      'FAILED',
      'RETRY_REQUIRED',
      'CANCELLED',
      'CREDITED'
    )
  )
);

CREATE TABLE etims_submission_history (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  submission_id UUID NOT NULL REFERENCES etims_submission_queue(id) ON DELETE CASCADE,
  status VARCHAR(32) NOT NULL,
  request_payload TEXT,
  response_payload TEXT,
  response_code VARCHAR(40),
  response_message VARCHAR(500),
  attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID REFERENCES branches(id),
  user_id UUID REFERENCES users(id),
  notification_type VARCHAR(80) NOT NULL,
  title VARCHAR(160) NOT NULL,
  message VARCHAR(500) NOT NULL,
  source_module VARCHAR(80),
  source_record_id UUID,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  read_at TIMESTAMPTZ,
  CONSTRAINT ck_notifications_status CHECK (status IN ('UNREAD', 'READ', 'DISMISSED'))
);

CREATE INDEX ix_purchase_requests_organization_created
  ON purchase_requests (organization_id, created_at DESC);
CREATE INDEX ix_purchase_orders_organization_created
  ON purchase_orders (organization_id, created_at DESC);
CREATE INDEX ix_goods_receipts_organization_received
  ON goods_receipts (organization_id, received_at DESC);
CREATE INDEX ix_supplier_invoices_organization_created
  ON supplier_invoices (organization_id, created_at DESC);
CREATE INDEX ix_crm_leads_organization_created
  ON crm_leads (organization_id, created_at DESC);
CREATE INDEX ix_crm_opportunities_organization_created
  ON crm_opportunities (organization_id, created_at DESC);
CREATE INDEX ix_crm_activities_organization_due
  ON crm_activities (organization_id, due_at);
CREATE INDEX ix_quotations_organization_created
  ON quotations (organization_id, created_at DESC);
CREATE INDEX ix_sales_orders_organization_created
  ON sales_orders (organization_id, created_at DESC);
CREATE INDEX ix_invoices_organization_created
  ON invoices (organization_id, created_at DESC);
CREATE INDEX ix_accounts_organization_type
  ON accounts (organization_id, account_type);
CREATE INDEX ix_journal_entries_organization_entry_date
  ON journal_entries (organization_id, entry_date DESC);
CREATE INDEX ix_etims_submission_queue_organization_status
  ON etims_submission_queue (organization_id, status, created_at DESC);
CREATE INDEX ix_notifications_organization_status
  ON notifications (organization_id, status, created_at DESC);

INSERT INTO permissions (id, code, description, created_at)
VALUES
  ('00000000-0000-4000-8000-000000000601', 'procurement:manage', 'Manage procurement requests, RFQs, purchase orders, receipts, and supplier invoices', now()),
  ('00000000-0000-4000-8000-000000000602', 'crm:manage', 'Manage CRM leads, opportunities, and activities', now()),
  ('00000000-0000-4000-8000-000000000603', 'commercial:manage', 'Manage quotations, sales orders, invoices, and customer receivables', now()),
  ('00000000-0000-4000-8000-000000000604', 'accounting:view', 'View accounting ledgers and financial summaries', now()),
  ('00000000-0000-4000-8000-000000000605', 'accounting:manage', 'Manage accounts, journals, periods, and tax rules', now()),
  ('00000000-0000-4000-8000-000000000606', 'etims:manage', 'Manage eTIMS configuration, queue, retries, and fiscal reports', now()),
  ('00000000-0000-4000-8000-000000000607', 'notifications:view', 'View operational notifications', now())
ON CONFLICT (code) DO UPDATE
SET description = excluded.description;

WITH role_permission_catalog(role_key, permission_code) AS (
  VALUES
    ('ADMIN', 'procurement:manage'),
    ('ADMIN', 'crm:manage'),
    ('ADMIN', 'commercial:manage'),
    ('ADMIN', 'accounting:view'),
    ('ADMIN', 'accounting:manage'),
    ('ADMIN', 'etims:manage'),
    ('ADMIN', 'notifications:view'),
    ('AUDITOR', 'accounting:view'),
    ('AUDITOR', 'notifications:view'),
    ('SHOP_MANAGER', 'procurement:manage'),
    ('SHOP_MANAGER', 'crm:manage'),
    ('SHOP_MANAGER', 'commercial:manage'),
    ('SHOP_MANAGER', 'notifications:view'),
    ('STORE_MANAGER', 'procurement:manage'),
    ('STORE_MANAGER', 'notifications:view'),
    ('SUPERVISOR', 'crm:manage'),
    ('SUPERVISOR', 'commercial:manage'),
    ('SUPERVISOR', 'notifications:view')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM role_permission_catalog catalog
JOIN roles role
  ON role.organization_id = '00000000-0000-4000-8000-000000000001'
  AND role.role_key = catalog.role_key
JOIN permissions permission
  ON permission.code = catalog.permission_code
ON CONFLICT DO NOTHING;
