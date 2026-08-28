-- Tables du module `auth`. Numérotation : 2xx (voir V101).

CREATE TABLE credentials (
    user_id             UUID        PRIMARY KEY,
    password_hash       VARCHAR(255) NOT NULL,
    password_changed_at TIMESTAMPTZ  NOT NULL
);

-- Pas de clé étrangère vers `users` : une table appartient à un seul module, et une
-- contrainte inter-modules recréerait par la base le couplage que l'architecture interdit.

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL,
    family_id   UUID        NOT NULL,
    token_hash  VARCHAR(64)    NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked     BOOLEAN     NOT NULL DEFAULT FALSE
);

-- La recherche se fait toujours par empreinte : c'est ce que présente le client.
CREATE UNIQUE INDEX refresh_tokens_hash_unique ON refresh_tokens (token_hash);
-- Révoquer une famille entière est le chemin chaud de la détection de rejeu.
CREATE INDEX refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user   ON refresh_tokens (user_id);
-- Sert la purge des jetons périmés.
CREATE INDEX refresh_tokens_expiry ON refresh_tokens (expires_at);

CREATE TABLE single_use_tokens (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL,
    purpose     VARCHAR(32) NOT NULL,
    token_hash  VARCHAR(64)    NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX single_use_tokens_hash_unique ON single_use_tokens (token_hash);
CREATE INDEX single_use_tokens_user_purpose ON single_use_tokens (user_id, purpose);
