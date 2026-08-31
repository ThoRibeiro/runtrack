-- L'historisation de fin de course (§4).
--
-- Ce que la table ajoute : les splits, la trace simplifiée et sa géométrie. Ce qu'elle n'ajoute
-- pas : une copie des statistiques. Celles-ci sont déjà figées par le fait qu'aucun point
-- n'arrive plus, et `activity_stats` en est la seule source ; une seconde copie ne pourrait que
-- diverger de la première.

CREATE TABLE activity_tracks (
    activity_id      UUID        PRIMARY KEY,
    -- La trace simplifiée, au format encoded polyline : quelques kilo-octets là où le JSON des
    -- points bruts en pèse des dizaines, et toutes les bibliothèques de carte savent la lire.
    polyline         TEXT        NOT NULL,
    point_count      INTEGER     NOT NULL,
    raw_point_count  INTEGER     NOT NULL,
    -- Nullable : une course arrêtée au premier point n'a pas de ligne, seulement une position.
    geom             GEOGRAPHY(LINESTRING, 4326),
    frozen_at        TIMESTAMPTZ NOT NULL,
    -- Renseignée quand les points bruts ont été purgés (90 jours, décision du lot 1). Elle évite
    -- de repasser chaque nuit sur les mêmes courses pour n'y rien trouver.
    points_purged_at TIMESTAMPTZ
);

-- La seule lecture du travail de purge : ce qui est archivé, assez vieux, et pas encore purgé.
CREATE INDEX activity_tracks_pending_purge ON activity_tracks (frozen_at)
    WHERE points_purged_at IS NULL;

CREATE TABLE activity_splits (
    activity_id         UUID             NOT NULL,
    kilometer_index     INTEGER          NOT NULL,
    distance_meters     DOUBLE PRECISION NOT NULL,
    time_seconds        BIGINT           NOT NULL,
    -- Absente quand le tronçon n'a pas duré : une allure sur zéro seconde n'est pas une allure.
    pace_seconds_per_km BIGINT,
    elevation_gain      DOUBLE PRECISION NOT NULL,
    average_heart_rate  DOUBLE PRECISION,
    PRIMARY KEY (activity_id, kilometer_index)
);
