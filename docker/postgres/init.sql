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

-- Current season must match the season in ranked_pstats seed data (1) so GET /api/players returns players
INSERT INTO current_season (season) VALUES (1);

-- Seed example data for local development/demo (not for production)

-- Users (plain roles; passwords are bcrypt hashes for the string "password")
INSERT INTO users (id, username, password, role) VALUES
    (1, 'admin',       '$2a$10$7EqJtq98hPqEX7fNZaFWoO5xkTqS.Acv16O3EOai9n1u/5g8vY/2u', 'admin'),
    (2, 'supervisor1', '$2a$10$7EqJtq98hPqEX7fNZaFWoO5xkTqS.Acv16O3EOai9n1u/5g8vY/2u', 'supervisor'),
    (3, 'supervisor2', '$2a$10$7EqJtq98hPqEX7fNZaFWoO5xkTqS.Acv16O3EOai9n1u/5g8vY/2u', 'supervisor'),
    (4, 'supervisor3', '$2a$10$7EqJtq98hPqEX7fNZaFWoO5xkTqS.Acv16O3EOai9n1u/5g8vY/2u', 'supervisor');

-- Players (names and nations inspired by old data)
INSERT INTO players (id, nickname, nation, rating, dz_rating, elo) VALUES
    (1, 'DuneWalker',   'no', 82, 82, 1200),
    (2, 'DepotSniper',  'se', 78, 78, 1150),
    (3, 'HothGuardian', 'fi', 74, 74, 1100),
    (4, 'GoazonRush',   'dk', 70, 70, 1080),
    (5, 'JawaRunner',   'de', 68, 68, 1050);

-- Ranked player stats for current season (season 1)
INSERT INTO ranked_pstats (id, player_id, season, br, best, played, won, lost, draw, score, mvp) VALUES
    (1, 1, 1, 1200, 1225, 10, 6, 3, 1,  350, 2),
    (2, 2, 1, 1150, 1175, 12, 7, 4, 1,  400, 3),
    (3, 3, 1, 1100, 1125,  8, 4, 3, 1,  260, 1),
    (4, 4, 1, 1080, 1100,  6, 3, 2, 1,  210, 0),
    (5, 5, 1, 1050, 1075,  5, 2, 2, 1,  180, 0);

-- Ranked matches (subset of real maps/rules)
INSERT INTO ranked_matches (id, map, rule, season, team_size, rebel_score, imperial_score, mvp, supervisor, date) VALUES
    (1, 'Dune Sea',          'DSE', 1, 4, 5, 3, 1, 2, '2024-01-01T20:00:00'),
    (2, 'Rebel Depot',       'DSE', 1, 4, 3, 5, 2, 2, '2024-01-02T21:10:00'),
    (3, 'Goazon Badlands',   'DSE', 1, 4, 4, 2, 3, 3, '2024-01-03T22:20:00'),
    (4, 'Twilight on Hoth',  'DSE', 1, 4, 2, 5, 4, 3, '2024-01-04T23:30:00'),
    (5, 'Jawa Refuge',       'DACE',1, 4, 5, 4, 5, 2, '2024-01-05T19:45:00');

-- Per-player stats per ranked match (each row links player to match)
INSERT INTO ranked_mstats (id, player_id, match_id, season, faction, result, score, perf, update_br, new_br) VALUES
    (1, 1, 1, 1, 'Rebel',    'Won', 120, 1.3,  +25, 1225),
    (2, 2, 1, 1, 'Imperial', 'Lost',  80, 0.8,  -15, 1135),
    (3, 3, 2, 1, 'Rebel',    'Lost',  70, 0.7,  -20, 1080),
    (4, 4, 3, 1, 'Imperial', 'Won',  110, 1.1,  +20, 1100),
    (5, 5, 5, 1, 'Rebel',    'Won',  130, 1.4,  +25, 1075);

-- Randomizer entries (map/rule pairs)
INSERT INTO randomizer (id, map, rule) VALUES
    (1, 'Dune Sea',         'DSE'),
    (2, 'Rebel Depot',      'DSE'),
    (3, 'Goazon Badlands',  'DSE'),
    (4, 'Twilight on Hoth', 'DSE'),
    (5, 'Jawa Refuge',      'DACE');

-- Adjust sequences so future inserts use ids above seeded data
SELECT setval('users_id_seq',         (SELECT MAX(id) FROM users));
SELECT setval('players_id_seq',       (SELECT MAX(id) FROM players));
SELECT setval('ranked_pstats_id_seq', (SELECT MAX(id) FROM ranked_pstats));
SELECT setval('ranked_matches_id_seq',(SELECT MAX(id) FROM ranked_matches));
SELECT setval('ranked_mstats_id_seq', (SELECT MAX(id) FROM ranked_mstats));
SELECT setval('randomizer_id_seq',    (SELECT MAX(id) FROM randomizer));