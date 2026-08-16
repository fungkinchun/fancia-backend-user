-- Drop denormalized display columns; clients resolve event details by event_id.
ALTER TABLE smart_match_icebreaker_events
    DROP COLUMN IF EXISTS next_start,
    DROP COLUMN IF EXISTS name,
    DROP COLUMN IF EXISTS location_label;
