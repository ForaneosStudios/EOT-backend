-- Additive migration: existing profiles remain intact and unlinked until migrated.
ALTER TABLE public.users ADD COLUMN IF NOT EXISTS auth_user_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'users_auth_user_id_key' AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE public.users ADD CONSTRAINT users_auth_user_id_key UNIQUE (auth_user_id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'users_auth_user_id_fkey' AND conrelid = 'public.users'::regclass
    ) THEN
        -- Deleting an Auth user is blocked while a profile references it.
        -- This avoids accidentally cascading into posts, threads and achievements.
        ALTER TABLE public.users ADD CONSTRAINT users_auth_user_id_fkey
            FOREIGN KEY (auth_user_id) REFERENCES auth.users(id) ON DELETE RESTRICT;
    END IF;
END $$;
