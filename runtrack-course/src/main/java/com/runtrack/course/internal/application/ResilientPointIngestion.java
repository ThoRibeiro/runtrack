package com.runtrack.course.internal.application;

import com.runtrack.course.internal.domain.track.TrackPoint;
import com.runtrack.shared.error.ConflictException;
import com.runtrack.shared.id.ActivityId;
import com.runtrack.shared.id.UserId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Réessaie l'ingestion quand deux lots se marchent dessus.
 *
 * <p>Le cahier des charges supposait « un seul écrivain par course ». C'est une hypothèse
 * de client, pas une garantie de serveur : un tampon rejoué pendant un retry réseau, ou
 * deux envois coup sur coup, produisent deux transactions concurrentes sur la même ligne de
 * statistiques. Le verrou optimiste les détecte ; sans reprise, le client verrait un échec
 * pour une situation parfaitement normale.
 *
 * <p>Composant distinct de {@link PointIngestion} parce que chaque tentative doit ouvrir sa
 * <em>propre</em> transaction : réessayer à l'intérieur d'une transaction déjà marquée
 * pour annulation ne servirait à rien.
 */
@Service
public class ResilientPointIngestion {

    private static final Logger LOG = LoggerFactory.getLogger(ResilientPointIngestion.class);
    private static final int MAX_ATTEMPTS = 3;

    private final PointIngestion ingestion;

    public ResilientPointIngestion(PointIngestion ingestion) {
        this.ingestion = ingestion;
    }

    public IngestionResult ingest(UserId ownerId, ActivityId activityId, List<TrackPoint> batch) {
        OptimisticLockingFailureException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return ingestion.ingest(ownerId, activityId, batch);
            } catch (OptimisticLockingFailureException conflict) {
                lastFailure = conflict;
                LOG.debug("Conflit d'ingestion sur {}, tentative {}/{}", activityId, attempt, MAX_ATTEMPTS);
            }
        }
        // Après trois échecs, ce n'est plus de la concurrence normale : le client doit le savoir.
        throw new ConflictException("INGESTION_CONFLICT",
                "Trop d'envois simultanés sur cette course, réessayez", lastFailure);
    }
}
