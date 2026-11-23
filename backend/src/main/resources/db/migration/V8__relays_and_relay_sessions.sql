-- Relay servers that forward the game's UDP packets, so hosts do not have to open a port. Edited by admins.
CREATE TABLE relays (
    id         VARCHAR(20)  PRIMARY KEY,
    region     VARCHAR(10)  NOT NULL,
    name       VARCHAR(60)  NOT NULL,
    host       VARCHAR(253) NOT NULL,
    port       INT          NOT NULL DEFAULT 25200 CHECK (port BETWEEN 1 AND 65535),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Running game servers (the server list). One live session per host token. A session without a heartbeat for
-- 90 seconds is gone; last_heartbeat_ms is Unix time in milliseconds so clock changes (summer time) cannot expire
-- or revive sessions. password_hash is a bcrypt hash; the password itself is never stored.
CREATE TABLE relay_sessions (
    sid               VARCHAR(32)  PRIMARY KEY,
    host_token_id     BIGINT       NOT NULL UNIQUE REFERENCES host_tokens(id) ON DELETE CASCADE,
    relay_id          VARCHAR(20)  NOT NULL REFERENCES relays(id) ON DELETE CASCADE,
    name              VARCHAR(40)  NOT NULL,
    mode              VARCHAR(40)  NOT NULL,
    level             VARCHAR(200) NOT NULL,
    players           INT          NOT NULL DEFAULT 0,
    max_players       INT          NOT NULL,
    password_hash     VARCHAR(100),
    auric_version     VARCHAR(20),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_heartbeat_ms BIGINT       NOT NULL
);
CREATE INDEX relay_sessions_last_heartbeat_idx ON relay_sessions (last_heartbeat_ms);
