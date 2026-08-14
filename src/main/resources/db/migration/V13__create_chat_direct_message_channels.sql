CREATE TABLE chat_direct_message_channels (
    deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP(6),
    created_by UUID,
    id UUID NOT NULL,
    first_user_id UUID NOT NULL,
    second_user_id UUID NOT NULL,
    channel_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chat_dm_channels_users UNIQUE (first_user_id, second_user_id),
    CONSTRAINT uk_chat_dm_channels_channel_id UNIQUE (channel_id)
);

CREATE INDEX chat_dm_channels_first_user_id_idx ON chat_direct_message_channels (first_user_id);
CREATE INDEX chat_dm_channels_second_user_id_idx ON chat_direct_message_channels (second_user_id);
