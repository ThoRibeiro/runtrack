-- La vignette de trace sur la ligne de fil.
--
-- Recopiée depuis `course` par la projection, comme le reste : le §10 interdit à `feed`
-- d'aller lire `activity_tracks`, et c'est cette recopie qui permet de servir une ligne
-- complète en une requête.
--
-- Nullable : une course en cours n'a pas encore de trace figée, et celles gelées avant
-- cette colonne n'en auront pas — la carte reste lisible sans.

ALTER TABLE feed_entries ADD COLUMN preview_polyline TEXT;
