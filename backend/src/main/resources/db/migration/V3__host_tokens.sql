-- API tokens for Auric hosts. Only the SHA-256 hash of a token is stored.
-- user_id is the admin/supervisor who owns the token and becomes the supervisor of matches uploaded with it.
CREATE TABLE host_tokens (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by   BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at   TIMESTAMP,
    last_used_at TIMESTAMP
);
