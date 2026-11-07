-- Waiters are names selected at a shared hotel sales counter, not login accounts.
CREATE TABLE hotel_waiters (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  name VARCHAR(160) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true
);
CREATE UNIQUE INDEX ux_hotel_waiter_name ON hotel_waiters (organization_id, branch_id, lower(name));

CREATE TABLE hotel_receipt_settings (
  branch_id UUID PRIMARY KEY REFERENCES branches(id),
  organization_id UUID NOT NULL REFERENCES organizations(id),
  hotel_name VARCHAR(160) NOT NULL,
  till_number VARCHAR(30) NOT NULL,
  contact_phone VARCHAR(40) NOT NULL
);

ALTER TABLE hotel_orders RENAME COLUMN served_by_id TO operator_id;
ALTER TABLE hotel_orders ADD COLUMN waiter_id UUID REFERENCES hotel_waiters(id);
ALTER TABLE hotel_orders ADD COLUMN hotel_name VARCHAR(160) NOT NULL DEFAULT '';
ALTER TABLE hotel_orders ADD COLUMN till_number VARCHAR(30) NOT NULL DEFAULT '';
ALTER TABLE hotel_orders ADD COLUMN contact_phone VARCHAR(40) NOT NULL DEFAULT '';
UPDATE hotel_orders o SET hotel_name = b.name FROM organizations b WHERE b.id = o.organization_id;

-- Preserve existing orders and their saved serving names while removing preparation tracking.
DROP INDEX IF EXISTS ix_hotel_kitchen_queue;
ALTER TABLE hotel_orders DROP CONSTRAINT IF EXISTS hotel_orders_kitchen_status_check;
ALTER TABLE hotel_orders RENAME COLUMN kitchen_status TO status;
UPDATE hotel_orders SET status = CASE
  WHEN status = 'CANCELLED' THEN 'CANCELLED'
  WHEN method = 'UNPAID' THEN 'OPEN'
  ELSE 'CLOSED' END;
ALTER TABLE hotel_orders ADD CONSTRAINT ck_hotel_order_status CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED'));
CREATE INDEX ix_hotel_open_orders ON hotel_orders (organization_id, branch_id, status, created_at);

INSERT INTO roles(id, organization_id, name, role_key, description)
SELECT gen_random_uuid(), id, 'Hotel Sales Manager', 'HOTEL_SALES_MANAGER',
       'Shared hotel sales counter: orders, receipts and collecting restaurant payments'
FROM organizations ON CONFLICT (organization_id, role_key) DO NOTHING;

DELETE FROM role_permissions rp USING roles r, permissions p
WHERE rp.role_id = r.id AND rp.permission_id = p.id
  AND ((r.role_key IN ('WAITER', 'KITCHEN', 'SHOP_MANAGER') AND p.code LIKE 'hotel:%')
    OR r.role_key = 'HOTEL_SALES_MANAGER' OR p.code = 'hotel:kitchen');

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.role_key = 'HOTEL_SALES_MANAGER' AND p.code = 'hotel:sell'
ON CONFLICT DO NOTHING;
DELETE FROM permissions WHERE code = 'hotel:kitchen';
