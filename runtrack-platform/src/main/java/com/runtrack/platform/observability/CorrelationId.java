package com.runtrack.platform.observability;

import com.runtrack.shared.context.CallContext;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Installe le contexte d'appel — et le fait suivre aux traitements asynchrones.
 *
 * <p><b>Le piège du §12, en une classe.</b> Un {@code ScopedValue} ne vit que dans la portée
 * dynamique de son {@code run} : il ne franchit ni un {@code @ApplicationModuleListener} — autre
 * fil, après commit —, ni le consommateur d'un Stream Dragonfly, ni un envoi push. Compter sur sa
 * propagation donne des journaux corrélés sur le seul chemin HTTP, et on ne s'en aperçoit qu'en
 * production, au moment précis où l'on cherche à relier une notification à la course qui l'a
 * provoquée.
 *
 * <p>La réponse est en deux temps, et les deux sont ici : le filtre HTTP <em>ouvre</em> la portée,
 * et chaque traitement asynchrone la <em>rouvre</em> à partir de l'identifiant que son événement
 * transporte. C'est pour cela que tous les événements du domaine portent un {@code correlationId} :
 * il n'est pas décoratif, c'est le seul chemin par lequel la corrélation traverse un fil.
 *
 * <p>Le MDC est posé en parallèle du {@code ScopedValue} : c'est lui que l'encodeur de journaux
 * lit, et il se nettoie dans un {@code finally} — un MDC qui fuit sur un fil de pool étiquette les
 * requêtes suivantes avec l'identifiant de la précédente, ce qui est pire que pas de corrélation
 * du tout.
 */
public final class CorrelationId {

    /** Le nom du champ dans les journaux et dans l'en-tête HTTP. */
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    /**
     * Rouvre une portée de corrélation autour d'un traitement asynchrone.
     *
     * <p>À appeler à l'entrée de tout écouteur d'événement : c'est ce qui relie la notification
     * envoyée à la requête HTTP qui, trois modules plus tôt, a démarré une course.
     */
    public static void resume(String correlationId, Runnable work) {
        String traced = correlationId == null || correlationId.isBlank() ? generate() : correlationId;
        MDC.put(MDC_KEY, traced);
        try {
            CallContext.anonymous(traced).runWith(work);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
