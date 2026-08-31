package com.runtrack.notification.internal.infra.jpa;

import com.runtrack.notification.internal.application.port.DeviceTokenRepository;
import com.runtrack.notification.internal.domain.push.DevicePlatform;
import com.runtrack.notification.internal.domain.push.DeviceToken;
import com.runtrack.shared.id.UserId;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Les appareils, en JDBC : une table plate, sans cycle de vie, lue par lot au moment du fan-out.
 *
 * <p>{@code ON CONFLICT ... DO UPDATE} plutôt que {@code DO NOTHING} : le même jeton peut changer
 * de propriétaire — un téléphone prêté, revendu, ou simplement une seconde session — et l'ignorer
 * laisserait les push partir vers l'ancien compte.
 */
@Repository
class JdbcDeviceTokenRepository implements DeviceTokenRepository {

    private static final String UPSERT = """
            INSERT INTO device_tokens (token, owner_id, platform, registered_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (token) DO UPDATE
                SET owner_id = EXCLUDED.owner_id,
                    platform = EXCLUDED.platform,
                    registered_at = EXCLUDED.registered_at
            """;

    private static final RowMapper<DeviceToken> MAPPER = (rs, rowNumber) -> new DeviceToken(
            rs.getString("token"),
            new UserId(rs.getObject("owner_id", UUID.class)),
            DevicePlatform.valueOf(rs.getString("platform")),
            rs.getObject("registered_at", OffsetDateTime.class).toInstant());

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    JdbcDeviceTokenRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    @Override
    public void register(DeviceToken device) {
        jdbc.update(UPSERT, device.token(), device.ownerId().value(), device.platform().name(),
                device.registeredAt().atOffset(ZoneOffset.UTC));
    }

    @Override
    public boolean forget(UserId ownerId, String token) {
        // Le propriétaire est dans le WHERE, pas dans un contrôle préalable : on ne retire jamais
        // l'appareil de quelqu'un d'autre, et c'est la requête qui le garantit.
        return jdbc.update("DELETE FROM device_tokens WHERE token = ? AND owner_id = ?",
                token, ownerId.value()) > 0;
    }

    @Override
    public List<DeviceToken> of(UserId ownerId) {
        return jdbc.query("SELECT * FROM device_tokens WHERE owner_id = ? ORDER BY registered_at DESC",
                MAPPER, ownerId.value());
    }

    @Override
    public List<DeviceToken> ofAll(Collection<UserId> ownerIds) {
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        // Un seul aller-retour pour tout le fan-out : une requête par abonné serait le N+1 du §10,
        // sur le chemin exact qu'un millier d'abonnés emprunte.
        return named.query("SELECT * FROM device_tokens WHERE owner_id IN (:owners)",
                Map.of("owners", ownerIds.stream().map(UserId::value).toList()), MAPPER);
    }

    @Override
    public int forgetAll(Collection<String> tokens) {
        if (tokens.isEmpty()) {
            return 0;
        }
        return named.update("DELETE FROM device_tokens WHERE token IN (:tokens)",
                Map.of("tokens", List.copyOf(tokens)));
    }
}
