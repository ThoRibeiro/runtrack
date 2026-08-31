-- Le registre de publications d'événements de Spring Modulith : l'outbox transactionnelle
-- du §7, qu'on n'écrit donc pas à la main.
--
-- Numérotation 0xx : elle ne porte aucune table métier et n'appartient à aucun module
-- fonctionnel. Elle passe avant toutes les autres parce que le premier événement peut être
-- publié dès le premier démarrage.
--
-- Le schéma appartient à Flyway, jamais à Hibernate : `ddl-auto: validate` fait échouer le
-- démarrage si cette table et l'entité de Modulith divergent — ce qui est exactement le
-- signal qu'on veut le jour d'une montée de version.

CREATE TABLE event_publication (
    id                     UUID        PRIMARY KEY,
    listener_id            TEXT        NOT NULL,
    event_type             TEXT        NOT NULL,
    -- TEXT, jamais VARCHAR(255) : un événement sérialisé dépasse largement cette taille.
    serialized_event       TEXT        NOT NULL,
    publication_date       TIMESTAMPTZ NOT NULL,
    completion_date        TIMESTAMPTZ,
    last_resubmission_date TIMESTAMPTZ,
    completion_attempts    INTEGER     NOT NULL,
    status                 TEXT
);

-- La question posée à chaque complétion : « cette publication-ci, pour cet écouteur-là ».
CREATE INDEX event_publication_by_listener
    ON event_publication (listener_id, serialized_event);

-- Et celle posée au redémarrage comme par la supervision : qu'est-ce qui n'a pas abouti ?
CREATE INDEX event_publication_incomplete
    ON event_publication (publication_date) WHERE completion_date IS NULL;
