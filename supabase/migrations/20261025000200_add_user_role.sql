-- Run once on an existing PostgreSQL database before starting the updated app.
-- Existing accounts remain USER; granting ADMIN must be a deliberate trusted action.
ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS role VARCHAR(10) NOT NULL DEFAULT 'USER';

-- Also normalize a nullable column if it was created earlier by Hibernate.
UPDATE public.users SET role = 'USER' WHERE role IS NULL;
ALTER TABLE public.users ALTER COLUMN role SET DEFAULT 'USER';
ALTER TABLE public.users ALTER COLUMN role SET NOT NULL;

-- Adding the constraint separately also covers a column created by Hibernate.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'users_role_allowed' AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE public.users ADD CONSTRAINT users_role_allowed
            CHECK (role IN ('USER', 'ADMIN'));
    END IF;
END $$;
