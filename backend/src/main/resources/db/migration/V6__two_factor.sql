-- Two-factor authentication (TOTP, authenticator app) for admins and editors.
-- totp_last_step is the last accepted 30-second time step, so a code cannot be used twice.
ALTER TABLE users
    ADD COLUMN totp_secret    VARCHAR(64),
    ADD COLUMN totp_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN totp_last_step BIGINT;

-- One-time recovery codes for a lost phone. Only the SHA-256 hash of each code is stored.
CREATE TABLE user_recovery_codes (
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    used_at   TIMESTAMP
);
