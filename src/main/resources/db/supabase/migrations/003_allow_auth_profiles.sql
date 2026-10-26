-- New Supabase Auth profiles do not store a local password. Existing hashes remain.
ALTER TABLE public.users ALTER COLUMN password DROP NOT NULL;
