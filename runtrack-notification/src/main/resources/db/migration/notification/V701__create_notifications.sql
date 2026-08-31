-- Tables du module `notification`. Numérotation : 7xx (voir V101).

CREATE TABLE notifications (
    -- Déduit de l'événement qui l'a provoquée, jamais tiré au sort : c'est cette clé qui rend
    -- un rejeu du registre Modulith sans effet (§7).
    id           UUID        PRIMARY KEY,
    recipient_id UUID        NOT NULL,
    type         VARCHAR(32) NOT NULL,
    actor_id     UUID,
    deep_link    TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    read_at      TIMESTAMPTZ
);

-- La lecture de la boîte : par destinataire, du plus récent au plus ancien, en pagination
-- par curseur. L'identifiant complète la date, sinon deux notifications écrites dans la même
-- milliseconde — ce qu'un fan-out produit en permanence — rendraient le curseur instable.
CREATE INDEX notifications_inbox ON notifications (recipient_id, created_at DESC, id DESC);

-- La pastille de non-lues, et le filtre « non lues seulement ». Index partiel : les lues sont
-- l'écrasante majorité et n'ont aucune raison de peser dessus.
CREATE INDEX notifications_unread ON notifications (recipient_id, created_at DESC)
    WHERE read_at IS NULL;

CREATE TABLE notification_preferences (
    user_id UUID PRIMARY KEY,
    -- Les natures coupées, pas celles activées : une nature ajoutée plus tard arrive ainsi
    -- allumée chez tout le monde, au lieu d'être éteinte sans que personne ne le sache.
    muted   TEXT NOT NULL
);
