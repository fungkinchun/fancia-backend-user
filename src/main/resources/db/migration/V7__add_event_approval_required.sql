ALTER TABLE events
    ADD COLUMN IF NOT EXISTS approval_required boolean NOT NULL DEFAULT true;
