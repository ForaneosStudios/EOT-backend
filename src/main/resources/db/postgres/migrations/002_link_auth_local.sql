-- Plain PostgreSQL has no Supabase auth schema. It mirrors the UUID and uniqueness,
-- but cannot enforce the auth.users foreign key without the Supabase stack.
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS auth_user_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS users_auth_user_id_local_key ON public.users (auth_user_id);
