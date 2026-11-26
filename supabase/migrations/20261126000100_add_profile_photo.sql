-- Optional profile photo taken with the device camera. The image itself lives in Supabase Storage
-- (public bucket, one 256x256 JPEG per player); users.profile_picture keeps its public URL.
-- profile_photo_consent_at records when the player accepted showing the photo to other people
-- and doubles as the "has a photo" marker (NULL = catalog avatar).
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_photo_consent_at TIMESTAMPTZ;

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('profile-photos', 'profile-photos', TRUE, 524288, ARRAY['image/jpeg'])
ON CONFLICT (id) DO UPDATE SET public = EXCLUDED.public,
                               file_size_limit = EXCLUDED.file_size_limit,
                               allowed_mime_types = EXCLUDED.allowed_mime_types;
