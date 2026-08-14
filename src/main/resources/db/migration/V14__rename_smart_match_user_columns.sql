ALTER TABLE smart_matches RENAME COLUMN user_id TO first_user_id;
ALTER TABLE smart_matches RENAME COLUMN target_id TO second_user_id;
ALTER TABLE smart_matches RENAME COLUMN user_id_flag TO first_user_liked;
ALTER TABLE smart_matches RENAME COLUMN target_id_flag TO second_user_liked;
ALTER TABLE smart_matches RENAME COLUMN user_id_flag_at TO first_user_liked_at;
ALTER TABLE smart_matches RENAME COLUMN target_id_flag_at TO second_user_liked_at;

ALTER TABLE smart_matches DROP CONSTRAINT IF EXISTS uk_smart_matches_user_target;
ALTER TABLE smart_matches
    ADD CONSTRAINT uk_smart_matches_first_second UNIQUE (first_user_id, second_user_id);

DROP INDEX IF EXISTS smart_matches_user_id_idx;
DROP INDEX IF EXISTS smart_matches_target_id_idx;
DROP INDEX IF EXISTS smart_matches_user_unseen_rank_idx;

CREATE INDEX smart_matches_first_user_id_idx ON smart_matches (first_user_id);
CREATE INDEX smart_matches_second_user_id_idx ON smart_matches (second_user_id);
CREATE INDEX smart_matches_first_unseen_rank_idx
    ON smart_matches (first_user_id, rank)
    WHERE first_user_liked IS NULL AND deleted = false;
