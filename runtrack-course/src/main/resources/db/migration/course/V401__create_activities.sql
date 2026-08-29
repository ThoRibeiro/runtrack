-- Tables du module `course`. Numérotation : 4xx (voir V101).

CREATE TABLE activities (
    id              UUID         PRIMARY KEY,
    owner_id        UUID         NOT NULL,
    type            VARCHAR(16)  NOT NULL,
    title           VARCHAR(120) NOT NULL,
    description     VARCHAR(2000),
    activity_scope  VARCHAR(16)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    status_since    TIMESTAMPTZ  NOT NULL,
    started_at      TIMESTAMPTZ  NOT NULL,
    clock_skew_nanos BIGINT      NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0
);

-- L'index du fil et de la liste d'un profil : par propriétaire, du plus récent au plus ancien.
CREATE INDEX activities_owner_recent ON activities (owner_id, started_at DESC);
-- L'écran « en direct » ne lit que les courses en cours : index partiel, donc minuscule.
CREATE INDEX activities_live ON activities (owner_id) WHERE status = 'Live';

CREATE TABLE activity_stats (
    activity_id           UUID   PRIMARY KEY,
    -- L'état complet de l'accumulateur, qui seul permet de reprendre l'incrémental où il
    -- s'est arrêté. Les colonnes qui suivent en sont dérivées, pour pouvoir trier et
    -- filtrer sans désérialiser.
    accumulator_state     JSONB  NOT NULL,
    last_applied_sequence INTEGER NOT NULL,
    distance_meters       DOUBLE PRECISION NOT NULL,
    moving_time_seconds   BIGINT NOT NULL,
    elevation_gain        DOUBLE PRECISION NOT NULL,
    elevation_loss        DOUBLE PRECISION NOT NULL
);
