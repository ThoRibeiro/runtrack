-- Tables du module `feed`. Numérotation : 8xx (voir V101).
--
-- Une projection de lecture, pas une copie de `activities`. Elle appartient à `feed`, qui la tient
-- à jour à partir des événements de `course` et `engagement` — c'est ce qui permet de servir une
-- ligne de fil complète en une requête, sans la jointure inter-modules qu'interdit le §10.
--
-- Fan-out à la lecture, décidé au lot 1 : il n'y a pas de ligne par destinataire, seulement une
-- par course, filtrée à la lecture sur la liste des abonnements.

CREATE TABLE feed_entries (
    activity_id         UUID             PRIMARY KEY,
    owner_id            UUID             NOT NULL,
    type                VARCHAR(32)      NOT NULL,
    title               VARCHAR(120)     NOT NULL,
    status              VARCHAR(32)      NOT NULL,
    -- La portée *effective*, déjà composée avec celle du compte par `course`. La projection la
    -- filtre sans rien recomposer, et un ActivityVisibilityChanged la met à jour.
    effective_scope     VARCHAR(16)      NOT NULL,
    distance_meters     DOUBLE PRECISION NOT NULL DEFAULT 0,
    moving_time_seconds BIGINT           NOT NULL DEFAULT 0,
    started_at          TIMESTAMPTZ      NOT NULL,
    ended_at            TIMESTAMPTZ,
    -- Les compteurs vivent ici, tenus par événements : c'est ce qui évite le N+1 du §10, où
    -- afficher vingt lignes coûterait quarante requêtes de comptage.
    like_count          BIGINT           NOT NULL DEFAULT 0,
    comment_count       BIGINT           NOT NULL DEFAULT 0
);

-- L'index du §10, et le seul chemin de lecture du fil.
CREATE INDEX feed_entries_owner_recent ON feed_entries (owner_id, started_at DESC);
