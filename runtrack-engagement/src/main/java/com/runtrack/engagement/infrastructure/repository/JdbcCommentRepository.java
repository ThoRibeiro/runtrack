package com.runtrack.engagement.infrastructure.repository;

import com.runtrack.engagement.usecases.port.CommentRepository;
import com.runtrack.engagement.usecases.model.interaction.Comment;
import com.runtrack.engagement.usecases.model.interaction.CommentId;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Les commentaires, en JDBC : écrits une fois, corrigés rarement, lus en page. */
@Repository
class JdbcCommentRepository implements CommentRepository {

    private static final String COLUMNS =
            "id, activity_id, author_id, parent_id, body, created_at, edited_at, deleted_at";

    private static final String UPSERT = """
            INSERT INTO comments (%s) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
                SET body = EXCLUDED.body,
                    edited_at = EXCLUDED.edited_at,
                    deleted_at = EXCLUDED.deleted_at
            """.formatted(COLUMNS);

    private static final RowMapper<Comment> MAPPER = (rs, rowNumber) -> new Comment(
            new CommentId(rs.getObject("id", UUID.class)),
            new ActivityId(rs.getObject("activity_id", UUID.class)),
            new UserId(rs.getObject("author_id", UUID.class)),
            Optional.ofNullable(rs.getObject("parent_id", UUID.class)).map(CommentId::new),
            rs.getString("body"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            Optional.ofNullable(rs.getObject("edited_at", OffsetDateTime.class))
                    .map(OffsetDateTime::toInstant),
            Optional.ofNullable(rs.getObject("deleted_at", OffsetDateTime.class))
                    .map(OffsetDateTime::toInstant));

    private final JdbcTemplate jdbc;

    JdbcCommentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Comment save(Comment comment) {
        // Ni la course, ni l'auteur, ni le parent ne changent jamais : les exclure de la mise à
        // jour rend impossible de déplacer un commentaire par accident.
        jdbc.update(UPSERT,
                comment.id().value(),
                comment.activityId().value(),
                comment.authorId().value(),
                comment.parentId().map(CommentId::value).orElse(null),
                comment.body(),
                comment.createdAt().atOffset(ZoneOffset.UTC),
                comment.editedAt().map(instant -> instant.atOffset(ZoneOffset.UTC)).orElse(null),
                comment.deletedAt().map(instant -> instant.atOffset(ZoneOffset.UTC)).orElse(null));
        return comment;
    }

    @Override
    public Optional<Comment> findById(CommentId id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM comments WHERE id = ?", MAPPER, id.value())
                .stream().findFirst();
    }

    @Override
    public List<Comment> ofActivity(ActivityId activityId, Optional<Instant> after, int limit) {
        var sql = new StringBuilder("SELECT " + COLUMNS + " FROM comments WHERE activity_id = ?");
        var arguments = new ArrayList<Object>();
        arguments.add(activityId.value());
        after.ifPresent(cursor -> {
            sql.append(" AND created_at > ?");
            arguments.add(cursor.atOffset(ZoneOffset.UTC));
        });
        sql.append(" ORDER BY created_at, id LIMIT ?");
        arguments.add(limit);
        return jdbc.query(sql.toString(), MAPPER, arguments.toArray());
    }

    @Override
    public long countFor(ActivityId activityId) {
        // Les supprimés ne comptent pas : le compteur affiché doit correspondre à ce qui se lit.
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM comments WHERE activity_id = ? AND deleted_at IS NULL",
                Long.class, activityId.value());
        return total == null ? 0 : total;
    }
}
