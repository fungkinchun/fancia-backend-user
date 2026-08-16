-- Relational icebreaker events (replaces smart_matches.icebreaker_events jsonb).
-- Display fields live on the event service; clients load them via event_id.
CREATE TABLE IF NOT EXISTS smart_match_icebreaker_events (
    id UUID NOT NULL,
    smart_match_id UUID NOT NULL,
    event_id UUID NOT NULL,
    score DOUBLE PRECISION NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_smart_match_icebreaker_match
        FOREIGN KEY (smart_match_id) REFERENCES smart_matches (id) ON DELETE CASCADE,
    CONSTRAINT uk_smart_match_icebreaker_match_event UNIQUE (smart_match_id, event_id)
);

CREATE INDEX IF NOT EXISTS smart_match_icebreaker_events_match_id_idx
    ON smart_match_icebreaker_events (smart_match_id);

CREATE TABLE IF NOT EXISTS smart_match_icebreaker_event_shared_tags (
    icebreaker_event_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    PRIMARY KEY (icebreaker_event_id, tag_id),
    CONSTRAINT fk_smart_match_icebreaker_shared_tag_event
        FOREIGN KEY (icebreaker_event_id) REFERENCES smart_match_icebreaker_events (id) ON DELETE CASCADE
);

ALTER TABLE smart_matches
    DROP COLUMN IF EXISTS icebreaker_events;
