CREATE TABLE users (
    id       BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role     VARCHAR(50) NOT NULL
);

CREATE TABLE players (
    id       BIGSERIAL PRIMARY KEY,
    nickname VARCHAR(100) NOT NULL,
    nation   VARCHAR(2) NOT NULL,
    rating   INT NOT NULL,
    dz_rating INT NOT NULL,
    elo      INT NOT NULL DEFAULT 0
);

-- seasonal statistics per player (before: season_stats)
CREATE TABLE ranked_pstats (
    id        BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    season    INT NOT NULL,
    br        INT NOT NULL DEFAULT 1000,
    best      INT NOT NULL DEFAULT 1000,
    played    INT NOT NULL DEFAULT 0,
    won       INT NOT NULL DEFAULT 0,
    lost      INT NOT NULL DEFAULT 0,
    draw      INT NOT NULL DEFAULT 0,
    score     INT NOT NULL DEFAULT 0,
    mvp       INT NOT NULL DEFAULT 0,
    UNIQUE (player_id, season)
);

-- Ranked-matches (before: matches)
CREATE TABLE ranked_matches (
    id             BIGSERIAL PRIMARY KEY,
    map            VARCHAR(100) NOT NULL,
    rule           VARCHAR(50) NOT NULL,
    season         INT NOT NULL,
    team_size      INT NOT NULL,
    rebel_score    INT NOT NULL,
    imperial_score INT NOT NULL,
    mvp            BIGINT REFERENCES players(id),
    supervisor     BIGINT NOT NULL REFERENCES users(id),
    date           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Per-player stat per ranked match (before: stats)
CREATE TABLE ranked_mstats (
    id         BIGSERIAL PRIMARY KEY,
    player_id  BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    match_id   BIGINT NOT NULL REFERENCES ranked_matches(id) ON DELETE CASCADE,
    season     INT NOT NULL,
    faction    VARCHAR(20) NOT NULL,
    result     VARCHAR(10) NOT NULL,
    score      INT NOT NULL,
    perf       DOUBLE PRECISION NOT NULL,
    update_br  INT NOT NULL,
    new_br     INT NOT NULL
);

CREATE TABLE randomizer (
    id   BIGSERIAL PRIMARY KEY,
    map  VARCHAR(100) NOT NULL,
    rule VARCHAR(50) NOT NULL
);

CREATE TABLE current_season (
    id     SERIAL PRIMARY KEY,
    season INT NOT NULL
);

INSERT INTO current_season (season) VALUES (1);