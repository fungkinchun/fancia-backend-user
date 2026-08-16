-- Pair Event Icebreakers: store top suggested events per smart_matches row
ALTER TABLE smart_matches
    ADD COLUMN IF NOT EXISTS icebreaker_events jsonb,
    ADD COLUMN IF NOT EXISTS icebreaker_computed_at timestamp without time zone;
