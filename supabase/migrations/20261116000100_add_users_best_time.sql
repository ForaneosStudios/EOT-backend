-- One best run per player. Time is measured in milliseconds; NULL means no completed run yet.
ALTER TABLE users ADD COLUMN IF NOT EXISTS best_time_ms BIGINT;
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_best_time_positive;
ALTER TABLE users ADD CONSTRAINT ck_users_best_time_positive CHECK (best_time_ms IS NULL OR best_time_ms > 0);
