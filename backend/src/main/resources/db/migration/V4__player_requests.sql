-- Requests from Auric hosts to register a player. An admin or editor approves (creates the player),
-- links the persona ID to an existing player, or rejects.
CREATE TABLE player_requests (
    id           BIGSERIAL PRIMARY KEY,
    persona_id   BIGINT       NOT NULL,
    nickname     VARCHAR(100) NOT NULL,
    nation       VARCHAR(2)   NOT NULL,
    rating       INT          NOT NULL,
    in_game_name VARCHAR(100),
    status       VARCHAR(20)  NOT NULL,
    token_id     BIGINT       REFERENCES host_tokens(id) ON DELETE SET NULL,
    requested_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at  TIMESTAMP,
    resolved_by  BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    player_id    BIGINT       REFERENCES players(id) ON DELETE SET NULL
);

-- At most one pending request per persona ID; a new request replaces the pending one.
CREATE UNIQUE INDEX player_requests_pending_persona ON player_requests (persona_id) WHERE status = 'pending';
