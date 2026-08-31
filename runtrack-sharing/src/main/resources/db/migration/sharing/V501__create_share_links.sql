-- Tables du module `sharing`. Numérotation : 5xx (voir V101).

CREATE TABLE share_links (
    id           UUID        PRIMARY KEY,
    activity_id  UUID        NOT NULL,
    created_by   UUID        NOT NULL,
    -- Le jeton n'est jamais stocké en clair : une fuite de la base ne doit pas ouvrir les
    -- courses partagées. C'est la même règle que pour un mot de passe, pour la même raison.
    -- SHA-256 et non Argon2 : un jeton de 256 bits tiré au sort n'a pas de dictionnaire,
    -- et le hachage lent protégerait d'une attaque qui n'existe pas ici tout en coûtant à
    -- chaque ouverture de lien.
    token_hash   CHAR(64)    NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    view_count   BIGINT      NOT NULL DEFAULT 0
);

-- La lecture la plus chaude : résoudre un jeton à chaque requête sur /shared/**.
CREATE UNIQUE INDEX share_links_token ON share_links (token_hash);

-- Et celle de l'écran de gestion : les liens d'une course, du plus récent au plus ancien.
CREATE INDEX share_links_activity ON share_links (activity_id, created_at DESC);
