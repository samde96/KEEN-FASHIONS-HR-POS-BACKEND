-- A receipt and its business mutation commit in the same PostgreSQL transaction.
-- Keep receipts indefinitely: a device may retry an old transaction after a long outage.
CREATE TABLE offline_operations (
  organization_id uuid NOT NULL REFERENCES organizations(id),
  id uuid NOT NULL,
  user_id uuid NOT NULL REFERENCES users(id),
  kind varchar(32) NOT NULL,
  request_payload jsonb NOT NULL,
  result_payload jsonb,
  occurred_at timestamptz NOT NULL,
  applied_at timestamptz,
  PRIMARY KEY (organization_id, id)
);
CREATE INDEX offline_operations_actor ON offline_operations(organization_id, user_id, occurred_at);
CREATE SEQUENCE offline_snapshot_revision;
ALTER TABLE stock_intakes ADD COLUMN stock_operation_id uuid REFERENCES stock_operations(id);
CREATE INDEX stock_intakes_operation ON stock_intakes(organization_id,stock_operation_id);

-- Validate device prices against actual published catalogue prices, including
-- prices cached before an outage. Never accept an invented browser price/VAT.
CREATE TABLE catalog_price_history (
  id bigserial PRIMARY KEY,
  organization_id uuid NOT NULL REFERENCES organizations(id),
  product_id uuid NOT NULL REFERENCES products(id),
  unit_price numeric(19,4) NOT NULL,
  vat_category varchar(4) NOT NULL,
  valid_from timestamptz NOT NULL,
  valid_to timestamptz
);
CREATE INDEX catalog_price_history_lookup ON catalog_price_history(organization_id,product_id);
INSERT INTO catalog_price_history(organization_id,product_id,unit_price,vat_category,valid_from)
SELECT organization_id,id,unit_price,vat_category,created_at FROM products;

CREATE FUNCTION record_catalog_price() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'UPDATE' THEN
    IF NEW.unit_price IS NOT DISTINCT FROM OLD.unit_price AND NEW.vat_category IS NOT DISTINCT FROM OLD.vat_category THEN
      RETURN NEW;
    END IF;
    UPDATE catalog_price_history SET valid_to=current_timestamp WHERE product_id=NEW.id AND valid_to IS NULL;
  END IF;
  INSERT INTO catalog_price_history(organization_id,product_id,unit_price,vat_category,valid_from)
  VALUES(NEW.organization_id,NEW.id,NEW.unit_price,NEW.vat_category,current_timestamp);
  RETURN NEW;
END $$;
CREATE TRIGGER record_catalog_price AFTER INSERT OR UPDATE OF unit_price,vat_category ON products
FOR EACH ROW EXECUTE FUNCTION record_catalog_price();
