package com.runtrack.platform.events;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * L'état du registre de publications, en lecture.
 *
 * <p>Interrogé en SQL et non par l'API de Modulith parce qu'on veut des <em>nombres</em>, pas
 * des objets : l'API sait rejouer une publication, elle ne sait pas dire combien il y en a.
 * Charger toutes les publications incomplètes pour les compter serait le contraire d'une
 * supervision — le jour où il y en a cent mille, c'est la mesure qui ferait tomber le service.
 */
@Component
public class EventPublications {

    private final JdbcTemplate jdbc;

    EventPublications(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Ce qui n'a pas encore abouti, quel qu'en soit le nombre de tentatives. */
    public long incompleteCount() {
        return count("SELECT count(*) FROM event_publication WHERE completion_date IS NULL");
    }

    /**
     * Ce qu'on a cessé de réessayer.
     *
     * <p>Une lettre morte n'est pas une erreur passagère : c'est un événement dont le traitement
     * échoue de façon reproductible, et qui demande une intervention. C'est le seul chiffre de
     * cette classe qui mérite une alerte.
     */
    public long deadLetteredCount(int maxAttempts) {
        return count("""
                SELECT count(*) FROM event_publication
                WHERE completion_date IS NULL AND completion_attempts >= ?
                """, maxAttempts);
    }

    /** L'âge de la plus vieille publication en souffrance : le vrai indicateur de dérive. */
    public Optional<Duration> oldestIncompleteAge(Instant now) {
        OffsetDateTime oldest = jdbc.queryForObject(
                "SELECT min(publication_date) FROM event_publication WHERE completion_date IS NULL",
                OffsetDateTime.class);
        return Optional.ofNullable(oldest).map(published -> Duration.between(published.toInstant(), now));
    }

    private long count(String sql, Object... arguments) {
        Long total = jdbc.queryForObject(sql, Long.class, arguments);
        return total == null ? 0 : total;
    }
}
