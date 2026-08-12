ALTER TABLE smart_matches RENAME COLUMN user_id TO target_id;

ALTER TABLE smart_matches ADD COLUMN user_id UUID;

UPDATE smart_matches
SET user_id = created_by
WHERE user_id IS NULL;

ALTER TABLE smart_matches ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE smart_matches ADD COLUMN user_id_flag BOOLEAN;
ALTER TABLE smart_matches ADD COLUMN target_id_flag BOOLEAN;
ALTER TABLE smart_matches ADD COLUMN user_id_flag_at TIMESTAMP(6);
ALTER TABLE smart_matches ADD COLUMN target_id_flag_at TIMESTAMP(6);
ALTER TABLE smart_matches ADD COLUMN rank INTEGER;
ALTER TABLE smart_matches ADD COLUMN score DOUBLE PRECISION;

UPDATE smart_matches
SET user_id_flag = CASE WHEN matched_by_created_by THEN TRUE ELSE NULL END,
    target_id_flag = CASE WHEN matched_by_user THEN TRUE ELSE NULL END,
    user_id_flag_at = CASE WHEN matched_by_created_by THEN created_at ELSE NULL END,
    target_id_flag_at = CASE WHEN matched_by_user THEN created_at ELSE NULL END;

ALTER TABLE smart_matches DROP COLUMN matched_by_created_by;
ALTER TABLE smart_matches DROP COLUMN matched_by_user;

ALTER TABLE smart_matches DROP CONSTRAINT IF EXISTS uk_smart_matches_created_by_user;

ALTER TABLE smart_matches
    ADD CONSTRAINT uk_smart_matches_user_target UNIQUE (user_id, target_id);

DROP INDEX IF EXISTS smart_matches_user_id_idx;
DROP INDEX IF EXISTS smart_matches_created_by_idx;

CREATE INDEX smart_matches_user_id_idx ON smart_matches (user_id);
CREATE INDEX smart_matches_target_id_idx ON smart_matches (target_id);
CREATE INDEX smart_matches_user_unseen_rank_idx
    ON smart_matches (user_id, rank)
    WHERE user_id_flag IS NULL AND deleted = false;
