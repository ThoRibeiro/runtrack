-- La vignette de trace : ce qu'une carte de fil dessine.
--
-- Une seconde polyline, beaucoup plus grossière que celle de l'écran de détail. La trace
-- d'affichage fait quelques milliers de points ; une liste de vingt courses qui les
-- télécharge toutes, c'est un mégaoctet pour des vignettes de cent points de côté.
--
-- Nullable : les courses historisées avant cette colonne n'en ont pas, et une carte sans
-- vignette reste une carte lisible. Elles la gagneront si elles sont regelées, pas avant.

ALTER TABLE activity_tracks ADD COLUMN preview_polyline TEXT;
