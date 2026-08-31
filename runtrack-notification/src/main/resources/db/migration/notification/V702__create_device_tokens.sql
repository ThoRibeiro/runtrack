-- Le push mobile : les appareils déclarés, et les heures pendant lesquelles se taire.

CREATE TABLE device_tokens (
    -- Le jeton est la clé, et pas un identifiant à nous : il change tout seul — réinstallation,
    -- restauration de sauvegarde — et le client le réenregistre sans savoir s'il en avait déjà un.
    token         VARCHAR(512) PRIMARY KEY,
    owner_id      UUID         NOT NULL,
    platform      VARCHAR(16)  NOT NULL,
    registered_at TIMESTAMPTZ  NOT NULL
);

-- La seule lecture qui existe : les appareils d'un lot de destinataires, au moment du fan-out.
CREATE INDEX device_tokens_owner ON device_tokens (owner_id);

-- Les heures calmes, dans le fuseau du destinataire — « pas avant 7 h » n'a de sens que là où il
-- se trouve. Les trois colonnes vont ensemble : soit une plage complète, soit rien.
ALTER TABLE notification_preferences
    ADD COLUMN quiet_from TIME,
    ADD COLUMN quiet_to   TIME,
    ADD COLUMN quiet_zone VARCHAR(64),
    ADD CONSTRAINT notification_preferences_quiet_hours_complete CHECK (
        (quiet_from IS NULL AND quiet_to IS NULL AND quiet_zone IS NULL)
        OR (quiet_from IS NOT NULL AND quiet_to IS NOT NULL AND quiet_zone IS NOT NULL));
