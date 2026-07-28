CREATE TABLE event_occurrences (
    deleted       BOOLEAN      NOT NULL DEFAULT false,
    created_at    TIMESTAMP(6),
    created_by    UUID,
    id            UUID         NOT NULL,
    event_id      UUID         NOT NULL,
    start_time    TIMESTAMP(6) NOT NULL,
    end_time      TIMESTAMP(6) NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    PRIMARY KEY (id),
    CONSTRAINT uk_event_occurrences_event_start UNIQUE (event_id, start_time),
    CONSTRAINT fk_event_occurrences_event FOREIGN KEY (event_id) REFERENCES events (id)
);

CREATE INDEX event_occurrences_event_id_idx ON event_occurrences (event_id);
CREATE INDEX event_occurrences_start_time_idx ON event_occurrences (start_time);

INSERT INTO event_occurrences (id, event_id, start_time, end_time, created_at, created_by, deleted, status)
SELECT gen_random_uuid(),
       e.id,
       e.start_time,
       e.end_time,
       e.created_at,
       e.created_by,
       e.deleted,
       'SCHEDULED'
FROM events e
WHERE e.start_time IS NOT NULL
  AND e.end_time IS NOT NULL;

ALTER TABLE event_participants
    ADD COLUMN occurrence_id UUID;

UPDATE event_participants ep
SET occurrence_id = (
    SELECT eo.id
    FROM event_occurrences eo
    WHERE eo.event_id = ep.event_id
    ORDER BY eo.start_time
    LIMIT 1
);

ALTER TABLE event_participants DROP CONSTRAINT fk2x391urx4up03f4jp2y9mdt5x;
ALTER TABLE event_participants DROP CONSTRAINT event_participants_pkey;
ALTER TABLE event_participants DROP COLUMN event_id;
ALTER TABLE event_participants ALTER COLUMN occurrence_id SET NOT NULL;
ALTER TABLE event_participants
    ADD CONSTRAINT event_participants_pkey PRIMARY KEY (occurrence_id, user_id);
ALTER TABLE event_participants
    ADD CONSTRAINT fk_event_participants_occurrence
        FOREIGN KEY (occurrence_id) REFERENCES event_occurrences (id);

ALTER TABLE reservations
    ADD COLUMN occurrence_id UUID;

UPDATE reservations r
SET occurrence_id = (
    SELECT eo.id
    FROM event_occurrences eo
    WHERE eo.event_id = r.event_id
    ORDER BY eo.start_time
    LIMIT 1
);

ALTER TABLE reservations DROP CONSTRAINT fkcnr8finplwp8whntrr02jpvre;
ALTER TABLE reservations DROP CONSTRAINT reservations_pkey;
ALTER TABLE reservations DROP COLUMN event_id;
ALTER TABLE reservations ALTER COLUMN occurrence_id SET NOT NULL;
ALTER TABLE reservations
    ADD CONSTRAINT reservations_pkey PRIMARY KEY (occurrence_id, user_id);
ALTER TABLE reservations
    ADD CONSTRAINT fk_reservations_occurrence
        FOREIGN KEY (occurrence_id) REFERENCES event_occurrences (id);
