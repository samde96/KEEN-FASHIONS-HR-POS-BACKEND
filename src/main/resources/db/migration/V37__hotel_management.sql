INSERT INTO permissions (id, code, description, created_at) VALUES
  (gen_random_uuid(), 'hotel:view', 'View hotel rooms, reservations and restaurant orders', now()),
  (gen_random_uuid(), 'hotel:manage', 'Manage hotel rooms, guests, payments, food sales and stock', now()),
  (gen_random_uuid(), 'hotel:sell', 'Take hotel orders, print bills and collect payments', now()),
  (gen_random_uuid(), 'hotel:kitchen', 'View and prepare hotel kitchen orders', now()),
  (gen_random_uuid(), 'hotel:reports', 'View hotel revenue and occupancy reports', now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO roles(id, organization_id, name, role_key, description)
SELECT gen_random_uuid(), o.id, r.name, r.key, r.description FROM organizations o
CROSS JOIN (VALUES ('Waiter', 'WAITER', 'Hotel table service and orders'),
                   ('Kitchen', 'KITCHEN', 'Hotel kitchen order preparation')) r(name,key,description)
ON CONFLICT (organization_id, role_key) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE (r.role_key = 'ADMIN' AND p.code LIKE 'hotel:%')
   OR (r.role_key = 'SHOP_MANAGER' AND p.code IN ('hotel:view', 'hotel:manage', 'hotel:sell', 'hotel:kitchen'))
   OR (r.role_key = 'WAITER' AND p.code = 'hotel:sell')
   OR (r.role_key = 'KITCHEN' AND p.code = 'hotel:kitchen')
ON CONFLICT DO NOTHING;

CREATE TABLE hotel_rooms (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  number VARCHAR(30) NOT NULL,
  room_type VARCHAR(80) NOT NULL,
  capacity INTEGER NOT NULL CHECK (capacity BETWEEN 1 AND 100),
  nightly_rate NUMERIC(14,2) NOT NULL CHECK (nightly_rate > 0),
  status VARCHAR(20) NOT NULL CHECK (status IN ('READY', 'CLEANING', 'MAINTENANCE')),
  UNIQUE (organization_id, branch_id, number)
);

CREATE TABLE hotel_bookings (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  room_id UUID NOT NULL REFERENCES hotel_rooms(id),
  reference VARCHAR(40) NOT NULL,
  guest_name VARCHAR(160) NOT NULL,
  guest_phone VARCHAR(40) NOT NULL DEFAULT '',
  guest_email VARCHAR(254) NOT NULL DEFAULT '',
  guests INTEGER NOT NULL CHECK (guests BETWEEN 1 AND 100),
  arrival DATE NOT NULL,
  departure DATE NOT NULL CHECK (departure > arrival),
  nightly_rate NUMERIC(14,2) NOT NULL CHECK (nightly_rate > 0),
  status VARCHAR(20) NOT NULL CHECK (status IN ('RESERVED', 'CHECKED_IN', 'CHECKED_OUT', 'CANCELLED')),
  notes VARCHAR(1000) NOT NULL DEFAULT '',
  checked_in_at TIMESTAMPTZ,
  checked_out_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (organization_id, reference)
);
CREATE INDEX ix_hotel_booking_calendar ON hotel_bookings (organization_id, branch_id, room_id, arrival, departure);

CREATE TABLE hotel_items (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  name VARCHAR(160) NOT NULL,
  category VARCHAR(20) NOT NULL CHECK (category IN ('PRODUCT', 'INGREDIENT', 'SUPPLY')),
  unit VARCHAR(30) NOT NULL,
  sellable BOOLEAN NOT NULL,
  price NUMERIC(14,2) NOT NULL CHECK (price >= 0),
  stock NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK (stock >= 0),
  reorder_level NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK (reorder_level >= 0),
  active BOOLEAN NOT NULL DEFAULT true,
  CHECK (NOT sellable OR (category = 'PRODUCT' AND price > 0)),
  UNIQUE (organization_id, branch_id, name)
);

-- Prepared dishes have prices and availability, independently of physical stock.
CREATE TABLE hotel_menu_items (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  name VARCHAR(160) NOT NULL,
  category VARCHAR(80) NOT NULL,
  description VARCHAR(500) NOT NULL DEFAULT '',
  price NUMERIC(14,2) NOT NULL CHECK (price > 0),
  available BOOLEAN NOT NULL DEFAULT true,
  UNIQUE (organization_id, branch_id, name)
);

CREATE TABLE hotel_orders (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  booking_id UUID REFERENCES hotel_bookings(id),
  reference VARCHAR(40) NOT NULL,
  customer_name VARCHAR(160) NOT NULL,
  total NUMERIC(14,2) NOT NULL CHECK (total > 0),
  method VARCHAR(10) NOT NULL CHECK (method IN ('UNPAID', 'CASH', 'MPESA', 'CARD', 'BANK', 'ROOM')),
  payment_reference VARCHAR(100) NOT NULL DEFAULT '',
  served_by_id UUID NOT NULL REFERENCES users(id),
  served_by_name VARCHAR(160) NOT NULL,
  table_number VARCHAR(30) NOT NULL DEFAULT '',
  service_type VARCHAR(10) NOT NULL CHECK (service_type IN ('DINE_IN', 'TAKEAWAY', 'DELIVERY')),
  notes VARCHAR(1000) NOT NULL DEFAULT '',
  kitchen_status VARCHAR(15) NOT NULL CHECK (kitchen_status IN ('PENDING', 'PREPARING', 'READY', 'SERVED', 'CANCELLED')),
  paid_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((method = 'ROOM' AND booking_id IS NOT NULL) OR (method <> 'ROOM' AND booking_id IS NULL)),
  UNIQUE (organization_id, reference)
);
CREATE INDEX ix_hotel_kitchen_queue ON hotel_orders (organization_id, branch_id, kitchen_status, created_at);
CREATE TABLE hotel_order_lines (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES hotel_orders(id),
  item_type VARCHAR(10) NOT NULL CHECK (item_type IN ('FOOD', 'PRODUCT')),
  menu_item_id UUID REFERENCES hotel_menu_items(id),
  stock_item_id UUID REFERENCES hotel_items(id),
  item_name VARCHAR(160) NOT NULL,
  quantity NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
  unit_price NUMERIC(14,2) NOT NULL CHECK (unit_price > 0),
  total NUMERIC(14,2) NOT NULL CHECK (total > 0),
  CHECK ((item_type = 'FOOD' AND menu_item_id IS NOT NULL AND stock_item_id IS NULL)
      OR (item_type = 'PRODUCT' AND stock_item_id IS NOT NULL AND menu_item_id IS NULL))
);

CREATE TABLE hotel_payments (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  booking_id UUID NOT NULL REFERENCES hotel_bookings(id),
  amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
  kind VARCHAR(10) NOT NULL CHECK (kind IN ('PAYMENT', 'REFUND')),
  method VARCHAR(10) NOT NULL CHECK (method IN ('CASH', 'MPESA', 'CARD', 'BANK')),
  reference VARCHAR(100) NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_hotel_payments_booking ON hotel_payments (organization_id, booking_id);

CREATE TABLE hotel_stock_movements (
  id UUID PRIMARY KEY,
  organization_id UUID NOT NULL REFERENCES organizations(id),
  branch_id UUID NOT NULL REFERENCES branches(id),
  item_id UUID NOT NULL REFERENCES hotel_items(id),
  quantity NUMERIC(14,3) NOT NULL CHECK (quantity <> 0),
  balance NUMERIC(14,3) NOT NULL CHECK (balance >= 0),
  kind VARCHAR(10) NOT NULL CHECK (kind IN ('RECEIPT', 'USAGE', 'SALE', 'RETURN')),
  note VARCHAR(500) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_hotel_movements_branch ON hotel_stock_movements (organization_id, branch_id, created_at DESC);

-- Branch row locks serialize hotel writes, including availability checks and retries.
CREATE TABLE hotel_commands (
  organization_id UUID NOT NULL REFERENCES organizations(id),
  request_id UUID NOT NULL,
  branch_id UUID NOT NULL REFERENCES branches(id),
  kind VARCHAR(20) NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  target_id UUID NOT NULL,
  PRIMARY KEY (organization_id, request_id)
);
