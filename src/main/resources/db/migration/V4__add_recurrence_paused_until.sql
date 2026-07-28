ALTER TABLE events
    ADD COLUMN recurrence_paused_until TIMESTAMP(6) WITH TIME ZONE;
