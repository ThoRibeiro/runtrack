package com.runtrack.sharing.internal.infra.jpa;

import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import com.runtrack.sharing.internal.application.port.ShareLinkRepository;
import com.runtrack.sharing.internal.domain.link.ShareLink;
import com.runtrack.sharing.internal.domain.link.ShareLinkId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Les liens de partage, en JDBC : une table plate, sans relation, et un compteur qui s'incrémente
 * en base plutôt qu'en mémoire.
 */
@Repository
class JdbcShareLinkRepository implements ShareLinkRepository {

    private static final String COLUMNS =
            "id, activity_id, created_by, token_hash, created_at, expires_at, revoked_at, view_count";

    private static final String UPSERT = """
            INSERT INTO share_links (%s)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET revoked_at = EXCLUDED.revoked_at
            """.formatted(COLUMNS);

    private static final RowMapper<ShareLink> MAPPER = (rs, rowNumber) -> new ShareLink(
            new ShareLinkId(rs.getObject("id", UUID.class)),
            new ActivityId(rs.getObject("activity_id", UUID.class)),
            new UserId(rs.getObject("created_by", UUID.class)),
            rs.getString("token_hash"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            Optional.ofNullable(rs.getObject("expires_at", OffsetDateTime.class))
                    .map(OffsetDateTime::toInstant),
            Optional.ofNullable(rs.getObject("revoked_at", OffsetDateTime.class))
                    .map(OffsetDateTime::toInstant),
            rs.getLong("view_count"));

    private final JdbcTemplate jdbc;

    JdbcShareLinkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ShareLink save(ShareLink link) {
        // Seule la révocation modifie un lien existant : le reste est immuable, et l'écrire ainsi
        // rend impossible de changer par accident la course qu'un lien ouvre.
        jdbc.update(UPSERT,
                link.id().value(),
                link.activityId().value(),
                link.createdBy().value(),
                link.tokenHash(),
                link.createdAt().atOffset(ZoneOffset.UTC),
                link.expiresAt().map(instant -> instant.atOffset(ZoneOffset.UTC)).orElse(null),
                link.revokedAt().map(instant -> instant.atOffset(ZoneOffset.UTC)).orElse(null),
                link.viewCount());
        return link;
    }

    @Override
    public Optional<ShareLink> findByTokenHash(String tokenHash) {
        return jdbc.query("SELECT " + COLUMNS + " FROM share_links WHERE token_hash = ?",
                MAPPER, tokenHash).stream().findFirst();
    }

    @Override
    public Optional<ShareLink> findById(ShareLinkId id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM share_links WHERE id = ?",
                MAPPER, id.value()).stream().findFirst();
    }

    @Override
    public List<ShareLink> ofActivity(ActivityId activityId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM share_links WHERE activity_id = ? ORDER BY created_at DESC",
                MAPPER, activityId.value());
    }

    @Override
    public void recordView(ShareLinkId id, Instant at) {
        jdbc.update("UPDATE share_links SET view_count = view_count + 1 WHERE id = ?", id.value());
    }
}
