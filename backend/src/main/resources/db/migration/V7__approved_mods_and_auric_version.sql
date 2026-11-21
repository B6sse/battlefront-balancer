-- Game mods approved and signed by the site owner (the files themselves are hosted elsewhere).
-- content_sha256 identifies the mod; ax_sha256/ax_size describe the signed file. An inactive mod counts as not approved.
CREATE TABLE approved_mods (
    id             BIGSERIAL PRIMARY KEY,
    content_sha256 VARCHAR(64)   NOT NULL UNIQUE,
    content_size   BIGINT        NOT NULL,
    title          VARCHAR(200)  NOT NULL,
    version        VARCHAR(50)   NOT NULL,
    author         VARCHAR(200)  NOT NULL,
    category       VARCHAR(20)   NOT NULL CHECK (category IN ('OPTIONAL', 'MUST_MATCH_HOST')),
    key_id         VARCHAR(100)  NOT NULL,
    ax_sha256      VARCHAR(64)   NOT NULL,
    ax_size        BIGINT        NOT NULL,
    download_url   VARCHAR(2000),
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    notes          TEXT,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- When the approved list last changed (create, edit, (de)activate or delete), for GET /api/mods/approved. One row.
CREATE TABLE approved_mods_state (
    id         INT       PRIMARY KEY CHECK (id = 1),
    updated_at TIMESTAMP NOT NULL
);
INSERT INTO approved_mods_state (id, updated_at) VALUES (1, CURRENT_TIMESTAMP);

-- Latest and minimum Auric version, for GET /api/auric/version. One row, created when an admin first saves it.
CREATE TABLE auric_version (
    id              INT           PRIMARY KEY CHECK (id = 1),
    latest_version  VARCHAR(20)   NOT NULL,
    minimum_version VARCHAR(20)   NOT NULL,
    download_url    VARCHAR(2000),
    sha256          VARCHAR(64),
    release_notes   VARCHAR(2000),
    updated_at      TIMESTAMP     NOT NULL
);
