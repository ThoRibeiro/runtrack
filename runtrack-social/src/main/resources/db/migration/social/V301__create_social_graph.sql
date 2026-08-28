-- Tables du module `social`. Numérotation : 3xx (voir V101).

CREATE TABLE follows (
    id           UUID        PRIMARY KEY,
    follower_id  UUID        NOT NULL,
    followee_id  UUID        NOT NULL,
    status       VARCHAR(16) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    accepted_at  TIMESTAMPTZ,
    CONSTRAINT follows_no_self CHECK (follower_id <> followee_id)
);

-- L'invariant « pas de doublon » vit d'abord dans le domaine ; l'index est ce qui tient
-- encore sous deux requêtes concurrentes, où deux transactions voient chacune un graphe vide.
CREATE UNIQUE INDEX follows_pair_unique ON follows (follower_id, followee_id);

-- Les deux sens de lecture : abonnés d'un compte, et abonnements d'un compte pour le fil.
CREATE INDEX follows_followee_accepted ON follows (followee_id) WHERE status = 'ACCEPTED';
CREATE INDEX follows_follower_accepted ON follows (follower_id) WHERE status = 'ACCEPTED';
-- La boîte de réception des demandes.
CREATE INDEX follows_pending ON follows (followee_id, requested_at DESC) WHERE status = 'PENDING';

CREATE TABLE blocks (
    id         UUID        PRIMARY KEY,
    blocker_id UUID        NOT NULL,
    blocked_id UUID        NOT NULL,
    at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT blocks_no_self CHECK (blocker_id <> blocked_id)
);

CREATE UNIQUE INDEX blocks_pair_unique ON blocks (blocker_id, blocked_id);
-- Le blocage se lit dans les deux sens : il faut donc un index dans chaque sens.
CREATE INDEX blocks_blocked ON blocks (blocked_id);
