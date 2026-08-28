-- Le module `user` possède cette table. Aucun autre module ne la lit directement :
-- ils passent par UserApi (§10).
--
-- Numérotation : chaque module a sa centaine réservée, sinon deux V1 dans deux
-- emplacements Flyway entrent en collision. user = 1xx, auth = 2xx, social = 3xx,
-- course = 4xx, sharing = 5xx, engagement = 6xx, notification = 7xx, feed = 8xx.

CREATE TABLE users (
    id                 UUID         PRIMARY KEY,
    handle             VARCHAR(30)  NOT NULL,
    email              VARCHAR(254) NOT NULL,
    display_name       VARCHAR(80)  NOT NULL,
    avatar_url         VARCHAR(2000),
    bio                VARCHAR(500),
    account_scope      VARCHAR(16)  NOT NULL,
    status             VARCHAR(24)  NOT NULL,
    birth_date         DATE,
    biological_sex     VARCHAR(16),
    weight_kilograms   DOUBLE PRECISION,
    height_centimeters DOUBLE PRECISION,
    registered_at      TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0
);

-- Unicité insensible à la casse. Les objets valeur normalisent déjà en minuscules ;
-- l'index est la garantie qui survit à un import ou à un correctif appliqué à la main.
CREATE UNIQUE INDEX users_handle_unique ON users (lower(handle));
CREATE UNIQUE INDEX users_email_unique  ON users (lower(email));

-- Sert la recherche par préfixe, comptes supprimés écartés.
CREATE INDEX users_display_name_search ON users (lower(display_name)) WHERE status <> 'DELETED';
