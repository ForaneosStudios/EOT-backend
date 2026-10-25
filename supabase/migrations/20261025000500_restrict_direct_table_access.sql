-- Application data is served through the Spring backend, not the Supabase Data API.
-- RLS remains enabled, and public API roles have no table privileges.
-- In particular, TRUNCATE is not subject to row-level policies.
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.achievements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_achievements ENABLE ROW LEVEL SECURITY;
REVOKE ALL PRIVILEGES ON TABLE public.users, public.categories, public.threads,
    public.posts, public.achievements, public.user_achievements FROM anon, authenticated;
