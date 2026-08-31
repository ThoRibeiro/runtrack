-- Tables du module `engagement`. Numérotation : 6xx (voir V101).

CREATE TABLE likes (
    -- La clé primaire *est* la règle « au plus un j'aime par personne et par course ». Deux clics
    -- simultanés ne peuvent pas produire deux lignes, là où un contrôle préalable en mémoire
    -- laisserait passer les deux.
    activity_id UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    liked_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (activity_id, user_id)
);

-- Le compteur et la liste d'une course : la clé primaire les sert déjà par son premier membre.
CREATE INDEX likes_activity_recent ON likes (activity_id, liked_at DESC);

CREATE TABLE comments (
    id          UUID          PRIMARY KEY,
    activity_id UUID          NOT NULL,
    author_id   UUID          NOT NULL,
    -- Une réponse pointe sur un commentaire de premier niveau de la même course. Pas de cascade :
    -- la suppression est logique, et un parent effacé garde ses réponses lisibles.
    parent_id   UUID          REFERENCES comments (id),
    body        VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    edited_at   TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ
);

-- La lecture du fil d'une course, du plus ancien au plus récent, en pagination par curseur.
CREATE INDEX comments_activity ON comments (activity_id, created_at, id);
