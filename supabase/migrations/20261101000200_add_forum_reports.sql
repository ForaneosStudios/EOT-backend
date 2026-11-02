CREATE TABLE IF NOT EXISTS public.post_reports (
    report_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id uuid NOT NULL REFERENCES public.posts(post_id) ON DELETE CASCADE,
    reporter_id uuid NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
    reason varchar(500) NOT NULL,
    status varchar(12) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RESOLVED', 'DISMISSED')),
    is_deleted boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz, deleted_at timestamptz,
    reviewed_at timestamptz
);
CREATE INDEX IF NOT EXISTS idx_post_reports_post ON public.post_reports(post_id, created_at DESC);
