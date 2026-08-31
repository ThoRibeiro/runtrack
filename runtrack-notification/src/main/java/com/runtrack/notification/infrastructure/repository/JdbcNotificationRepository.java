package com.runtrack.notification.infrastructure.repository;

import com.runtrack.notification.usecases.port.NotificationRepository;
import com.runtrack.notification.usecases.model.inbox.Notification;
import com.runtrack.notification.usecases.model.inbox.NotificationId;
import com.runtrack.notification.usecases.model.inbox.NotificationType;
import com.runtrack.shared.id.UserId;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * La boîte de réception, en JDBC direct plutôt qu'en JPA.
 *
 * <p>Une notification n'est pas un agrégat : elle naît, elle est lue, elle ne change plus. Le seul
 * chemin d'écriture est un fan-out qui en produit des centaines d'un coup, et le seul chemin de
 * lecture une page triée. Passer par un {@code EntityManager} ferait grossir le contexte de
 * persistance à chaque course démarrée, pour aucun bénéfice.
 *
 * <p>{@code ON CONFLICT DO NOTHING} est le filet d'idempotence du §7 : un rejeu présente les mêmes
 * identifiants déduits, la base les ignore, et {@code RETURNING} ne rend que ce qui a réellement
 * été inséré — donc seulement ce qu'il faut diffuser.
 */
@Repository
class JdbcNotificationRepository implements NotificationRepository {

    private static final String INSERT = """
            INSERT INTO notifications (id, recipient_id, type, actor_id, deep_link, created_at, read_at)
            VALUES (?, ?, ?, ?, ?, ?, NULL)
            ON CONFLICT (id) DO NOTHING
            """;

    /**
     * L'agrégation : la ligne remonte en tête, redevient non lue, et son compteur avance.
     *
     * <p>Tout se joue dans un seul {@code UPDATE} : lire puis réécrire ferait perdre l'un des deux
     * « j'aime » arrivés en même temps, ce qui est exactement le cas où le compteur sert.
     */
    private static final String AGGREGATE = """
            INSERT INTO notifications (id, recipient_id, type, actor_id, deep_link, created_at,
                                       read_at, aggregate_count)
            VALUES (?, ?, ?, ?, ?, ?, NULL, 1)
            ON CONFLICT (id) DO UPDATE SET
                aggregate_count = notifications.aggregate_count + 1,
                actor_id = EXCLUDED.actor_id,
                created_at = EXCLUDED.created_at,
                read_at = NULL
            RETURNING aggregate_count
            """;

    private static final String COLUMNS =
            "id, recipient_id, type, actor_id, deep_link, created_at, read_at, aggregate_count";

    private static final RowMapper<Notification> MAPPER = (rs, rowNumber) -> new Notification(
            new NotificationId(rs.getObject("id", UUID.class)),
            new UserId(rs.getObject("recipient_id", UUID.class)),
            NotificationType.valueOf(rs.getString("type")),
            Optional.ofNullable(rs.getObject("actor_id", UUID.class)).map(UserId::new),
            rs.getString("deep_link"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            Optional.ofNullable(rs.getObject("read_at", OffsetDateTime.class))
                    .map(OffsetDateTime::toInstant),
            rs.getInt("aggregate_count"));

    private final JdbcTemplate jdbc;

    JdbcNotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Notification> appendAll(List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return List.of();
        }
        int[] written = jdbc.batchUpdate(INSERT, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement statement, int index) throws SQLException {
                Notification notification = notifications.get(index);
                statement.setObject(1, notification.id().value());
                statement.setObject(2, notification.recipientId().value());
                statement.setString(3, notification.type().name());
                setNullableUuid(statement, 4, notification.actorId().map(UserId::value));
                statement.setString(5, notification.deepLink());
                statement.setObject(6, notification.createdAt().atOffset(ZoneOffset.UTC));
            }

            @Override
            public int getBatchSize() {
                return notifications.size();
            }
        });

        // Le lot rend une ligne par insertion tentée : celles à zéro sont les rejeux, et elles ne
        // doivent surtout pas repartir en temps réel — le destinataire les a déjà reçues.
        var inserted = new ArrayList<Notification>(notifications.size());
        for (int index = 0; index < notifications.size(); index++) {
            if (written[index] > 0) {
                inserted.add(notifications.get(index));
            }
        }
        return List.copyOf(inserted);
    }

    @Override
    public Notification aggregate(Notification notification) {
        Integer total = jdbc.queryForObject(AGGREGATE, Integer.class,
                notification.id().value(),
                notification.recipientId().value(),
                notification.type().name(),
                notification.actorId().map(UserId::value).orElse(null),
                notification.deepLink(),
                notification.createdAt().atOffset(ZoneOffset.UTC));

        return new Notification(notification.id(), notification.recipientId(), notification.type(),
                notification.actorId(), notification.deepLink(), notification.createdAt(),
                Optional.empty(), total == null ? 1 : total);
    }

    @Override
    public List<Notification> findFor(UserId recipientId, Optional<Instant> before, boolean unreadOnly,
            int limit) {

        var sql = new StringBuilder("SELECT " + COLUMNS + " FROM notifications WHERE recipient_id = ?");
        var arguments = new ArrayList<Object>();
        arguments.add(recipientId.value());
        if (unreadOnly) {
            sql.append(" AND read_at IS NULL");
        }
        before.ifPresent(cursor -> {
            sql.append(" AND created_at < ?");
            arguments.add(cursor.atOffset(ZoneOffset.UTC));
        });
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        arguments.add(limit);

        return jdbc.query(sql.toString(), MAPPER, arguments.toArray());
    }

    @Override
    public Optional<Notification> find(UserId recipientId, NotificationId id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM notifications WHERE recipient_id = ? AND id = ?",
                MAPPER, recipientId.value(), id.value()).stream().findFirst();
    }

    @Override
    public boolean markRead(UserId recipientId, NotificationId id, Instant when) {
        return jdbc.update("""
                UPDATE notifications SET read_at = ?
                WHERE recipient_id = ? AND id = ? AND read_at IS NULL
                """, when.atOffset(ZoneOffset.UTC), recipientId.value(), id.value()) > 0;
    }

    @Override
    public int markAllRead(UserId recipientId, Instant when) {
        return jdbc.update(
                "UPDATE notifications SET read_at = ? WHERE recipient_id = ? AND read_at IS NULL",
                when.atOffset(ZoneOffset.UTC), recipientId.value());
    }

    @Override
    public long unreadCount(UserId recipientId) {
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE recipient_id = ? AND read_at IS NULL",
                Long.class, recipientId.value());
        return total == null ? 0 : total;
    }

    private static void setNullableUuid(PreparedStatement statement, int index, Optional<UUID> value)
            throws SQLException {

        if (value.isPresent()) {
            statement.setObject(index, value.get());
        } else {
            statement.setNull(index, Types.OTHER);
        }
    }
}
