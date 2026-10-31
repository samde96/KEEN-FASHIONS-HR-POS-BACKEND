ALTER TABLE pos_preferences DROP CONSTRAINT pos_preferences_inactivity_minutes_check;
ALTER TABLE pos_preferences ADD CONSTRAINT pos_preferences_inactivity_minutes_check
  CHECK (inactivity_minutes BETWEEN 1 AND 525600);
ALTER TABLE pos_preferences ALTER COLUMN inactivity_minutes SET DEFAULT 2880;

-- Replace the original five-minute default with two days; retain other custom intervals.
UPDATE pos_preferences SET inactivity_minutes = 2880 WHERE inactivity_minutes = 5;
