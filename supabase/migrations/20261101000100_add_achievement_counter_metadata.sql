ALTER TABLE public.achievements
  ADD COLUMN IF NOT EXISTS target_value INTEGER,
  ADD COLUMN IF NOT EXISTS progress_label VARCHAR(50);

ALTER TABLE public.achievements
  DROP CONSTRAINT IF EXISTS achievements_counter_metadata_check;

ALTER TABLE public.achievements
  ADD CONSTRAINT achievements_counter_metadata_check CHECK (
    (target_value IS NULL AND progress_label IS NULL)
    OR (target_value IS NOT NULL AND target_value > 0 AND progress_label IS NOT NULL)
  );
