-- EA persona ID (stable in-game identity) and the name the player last used in game.
-- Both are optional: older players have no known persona ID.
ALTER TABLE players
    ADD COLUMN persona_id     BIGINT UNIQUE,
    ADD COLUMN last_seen_name VARCHAR(100);
