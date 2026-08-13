ALTER TABLE users DROP CONSTRAINT IF EXISTS users_visibility_check;

UPDATE user_settings us
SET privacy = jsonb_set(
    COALESCE(us.privacy, '{}'::jsonb),
    '{smartMatchEnabled}',
    'true'::jsonb,
    true
)
FROM users u
WHERE u.id = us.user_id
  AND u.visibility = 'SMART_MATCH_ENABLED';

UPDATE users
SET visibility = 'PUBLIC'
WHERE visibility = 'SMART_MATCH_ENABLED';

ALTER TABLE users ALTER COLUMN visibility TYPE VARCHAR(16);

ALTER TABLE users
    ADD CONSTRAINT users_visibility_check
        CHECK (visibility IN ('PUBLIC', 'PRIVATE'));
