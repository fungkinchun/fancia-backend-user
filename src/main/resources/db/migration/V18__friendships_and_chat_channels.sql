-- Friendships
CREATE TABLE friendships (
    deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP(6),
    created_by UUID,
    id UUID NOT NULL,
    requester_id UUID NOT NULL,
    addressee_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    responded_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT friendships_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT friendships_not_self_check CHECK (requester_id <> addressee_id)
);

COMMENT ON COLUMN friendships.deleted IS 'Soft-delete indicator';

CREATE UNIQUE INDEX uk_friendships_active_pair
    ON friendships (
        LEAST(requester_id, addressee_id),
        GREATEST(requester_id, addressee_id)
    )
    WHERE status IN ('PENDING', 'ACCEPTED') AND deleted = false;

CREATE INDEX friendships_requester_id_idx ON friendships (requester_id);
CREATE INDEX friendships_addressee_id_idx ON friendships (addressee_id);
CREATE INDEX friendships_status_idx ON friendships (status);

-- Unify DM + group-inquiry channel registry (evolve V13 chat_direct_message_channels)
ALTER TABLE chat_direct_message_channels RENAME TO chat_channels;

ALTER TABLE chat_channels RENAME CONSTRAINT uk_chat_dm_channels_channel_id TO uk_chat_channels_channel_id;
ALTER TABLE chat_channels DROP CONSTRAINT IF EXISTS uk_chat_dm_channels_users;

ALTER INDEX IF EXISTS chat_dm_channels_first_user_id_idx RENAME TO chat_channels_first_user_id_idx;
ALTER INDEX IF EXISTS chat_dm_channels_second_user_id_idx RENAME TO chat_channels_second_user_id_idx;

ALTER TABLE chat_channels
    ADD COLUMN kind VARCHAR(32) NOT NULL DEFAULT 'DM',
    ADD COLUMN interest_group_id UUID,
    ADD COLUMN initiator_user_id UUID;

ALTER TABLE chat_channels
    ALTER COLUMN first_user_id DROP NOT NULL,
    ALTER COLUMN second_user_id DROP NOT NULL;

ALTER TABLE chat_channels
    ADD CONSTRAINT chat_channels_kind_check CHECK (kind IN ('DM', 'GROUP_INQUIRY'));

-- Existing rows are 1:1 DMs; keep canonical pair unique for live DM channels.
CREATE UNIQUE INDEX uk_chat_channels_dm_pair
    ON chat_channels (first_user_id, second_user_id)
    WHERE kind = 'DM'
      AND deleted = false
      AND first_user_id IS NOT NULL
      AND second_user_id IS NOT NULL;

CREATE UNIQUE INDEX uk_chat_channels_group_inquiry_pair
    ON chat_channels (interest_group_id, initiator_user_id)
    WHERE kind = 'GROUP_INQUIRY'
      AND deleted = false
      AND interest_group_id IS NOT NULL
      AND initiator_user_id IS NOT NULL;

CREATE INDEX chat_channels_kind_idx ON chat_channels (kind);
CREATE INDEX chat_channels_interest_group_id_idx ON chat_channels (interest_group_id);
CREATE INDEX chat_channels_initiator_user_id_idx ON chat_channels (initiator_user_id);

CREATE TABLE chat_channel_members (
    deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP(6),
    created_by UUID,
    id UUID NOT NULL,
    chat_channel_id UUID NOT NULL,
    user_id UUID NOT NULL,
    joined_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_channel_members_channel
        FOREIGN KEY (chat_channel_id) REFERENCES chat_channels (id)
);

COMMENT ON COLUMN chat_channel_members.deleted IS 'Soft-delete indicator';

CREATE UNIQUE INDEX uk_chat_channel_members_channel_user
    ON chat_channel_members (chat_channel_id, user_id)
    WHERE deleted = false;

CREATE INDEX chat_channel_members_user_id_idx ON chat_channel_members (user_id);
CREATE INDEX chat_channel_members_chat_channel_id_idx ON chat_channel_members (chat_channel_id);

-- Backfill members for existing DM channels (joined_at ≈ channel created_at).
INSERT INTO chat_channel_members (deleted, created_at, created_by, id, chat_channel_id, user_id, joined_at)
SELECT false,
       COALESCE(c.created_at, NOW()),
       c.created_by,
       gen_random_uuid(),
       c.id,
       c.first_user_id,
       COALESCE(c.created_at, NOW())
FROM chat_channels c
WHERE c.kind = 'DM'
  AND c.first_user_id IS NOT NULL
  AND c.deleted = false;

INSERT INTO chat_channel_members (deleted, created_at, created_by, id, chat_channel_id, user_id, joined_at)
SELECT false,
       COALESCE(c.created_at, NOW()),
       c.created_by,
       gen_random_uuid(),
       c.id,
       c.second_user_id,
       COALESCE(c.created_at, NOW())
FROM chat_channels c
WHERE c.kind = 'DM'
  AND c.second_user_id IS NOT NULL
  AND c.deleted = false;
