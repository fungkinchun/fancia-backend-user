CREATE TABLE user_blacklist_ids (
    user_id UUID NOT NULL,
    blacklisted_id UUID NOT NULL,
    PRIMARY KEY (user_id, blacklisted_id),
    CONSTRAINT fk_user_blacklist_ids_user FOREIGN KEY (user_id) REFERENCES "users" (id)
);

CREATE INDEX user_blacklist_ids_blacklisted_id_idx ON user_blacklist_ids (blacklisted_id);
