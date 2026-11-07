ALTER TABLE hotel_waiters
  ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX ux_hotel_waiter_user
  ON hotel_waiters (organization_id, branch_id, user_id)
  WHERE user_id IS NOT NULL;
