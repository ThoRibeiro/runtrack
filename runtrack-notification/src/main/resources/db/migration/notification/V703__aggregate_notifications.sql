-- L'agrégation du §7 : « Marie et 4 autres ont aimé ».
--
-- Un compteur sur la notification, plutôt qu'une notification par « j'aime ». Vingt personnes qui
-- aiment la même course, ce sont vingt interruptions pour un seul fait — et une boîte de réception
-- où le reste devient illisible.

ALTER TABLE notifications
    ADD COLUMN aggregate_count INTEGER NOT NULL DEFAULT 1;
