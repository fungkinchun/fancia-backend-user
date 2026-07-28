ALTER TABLE events
    ADD COLUMN recurrence_frequency VARCHAR(32) NOT NULL DEFAULT 'NONE',
    ADD COLUMN recurrence_days_mask SMALLINT NOT NULL DEFAULT 0;

CREATE TABLE smart_matches (
    deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP(6),
    created_by UUID,
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    matched_by_user BOOLEAN NOT NULL DEFAULT false,
    matched_by_created_by BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (id),
    CONSTRAINT uk_smart_matches_created_by_user UNIQUE (created_by, user_id)
);

CREATE INDEX smart_matches_user_id_idx ON smart_matches (user_id);
CREATE INDEX smart_matches_created_by_idx ON smart_matches (created_by);
