-- Date de dernière modification du profil.
--
-- Rétroactivement, la seule valeur honnête pour une ligne existante est sa date d'inscription :
-- rien n'a été enregistré depuis, et dater ces lignes de la migration prétendrait qu'un
-- changement a eu lieu aujourd'hui.
--
-- La colonne naît nullable, se remplit, puis devient obligatoire : un ALTER en NOT NULL direct
-- échouerait sur les lignes déjà là.
ALTER TABLE users ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE users SET updated_at = registered_at WHERE updated_at IS NULL;

ALTER TABLE users ALTER COLUMN updated_at SET NOT NULL;
