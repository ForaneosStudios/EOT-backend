ALTER TABLE public.post_reports
    ADD COLUMN IF NOT EXISTS reviewed_at timestamptz;
