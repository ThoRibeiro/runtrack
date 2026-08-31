-- Les points de trace et l'idempotence de leur ingestion.

CREATE TABLE track_points (
    activity_id     UUID             NOT NULL,
    sequence_number INTEGER          NOT NULL,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    elevation       DOUBLE PRECISION NOT NULL,
    recorded_at     TIMESTAMPTZ      NOT NULL,
    accuracy_meters DOUBLE PRECISION NOT NULL,
    heart_rate      INTEGER,
    cadence         INTEGER,
    geom            GEOGRAPHY(POINT, 4326) NOT NULL,

    -- La clé primaire *est* la garantie d'idempotence. Rejouer un lot ne peut pas
    -- dupliquer un point, quelles que soient les conditions de course en amont.
    PRIMARY KEY (activity_id, sequence_number)
);

-- La lecture d'une trace se fait toujours dans l'ordre du temps.
CREATE INDEX track_points_activity_time ON track_points (activity_id, recorded_at);
-- Index géographique, pour les recherches spatiales à venir.
CREATE INDEX track_points_geom ON track_points USING GIST (geom);

-- Table non partitionnée, et c'est délibéré : sous PostgreSQL, un index unique sur une
-- table partitionnée doit contenir la clé de partition. Partitionner par mois rendrait
-- (activity_id, sequence_number) impossible, or c'est le pilier de l'idempotence. Si le
-- volume l'impose un jour, ce sera un partitionnement par hash sur activity_id.

ALTER TABLE activity_stats ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE idempotency_keys (
    activity_id     UUID         NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    -- VARCHAR et non CHAR : CHAR complète à droite avec des espaces, et une empreinte
    -- comparée après remplissage n'est plus une empreinte.
    request_digest  VARCHAR(64)  NOT NULL,
    response_body   TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,

    PRIMARY KEY (activity_id, idempotency_key)
);

-- La clé n'a de portée qu'au sein d'une course : deux courses peuvent réutiliser la même
-- sans se gêner, et le client n'a pas à garantir une unicité globale.
CREATE INDEX idempotency_keys_expiry ON idempotency_keys (created_at);
