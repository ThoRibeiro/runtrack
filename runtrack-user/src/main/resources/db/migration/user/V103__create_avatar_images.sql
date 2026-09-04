-- Les photos de profil, dans la base plutôt que sur un disque monté.
--
-- Ce sont des vignettes de quelques centaines de kilo-octets, une par compte : le
-- volume ne justifie pas un stockage objet, et la sauvegarde de la base emporte
-- les images avec elle — un fichier oublié sur un volume, non.
--
-- Une seule image vivante par compte : l'ancienne est supprimée au remplacement,
-- sinon la table grossit d'un fichier à chaque essai de photo.

CREATE TABLE avatar_images (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    bytes        BYTEA       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX avatar_images_user ON avatar_images (user_id);
