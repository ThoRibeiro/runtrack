/**
 * Le domaine riche : cycle de vie d'une course, ingestion GPS, statistiques, live.
 * Dépend de {@code user} et {@code social} pour résoudre la {@code ViewerRelation} que sa
 * règle d'accès consomme — la résolution vit dans {@code application}, jamais dans le
 * domaine. Ne connaît en revanche ni {@code sharing} (le {@code Viewer} vient de
 * {@code shared}) ni {@code engagement} : c'est ce qui garde le graphe acyclique.
 */
@org.springframework.modulith.ApplicationModule(displayName = "course",
        allowedDependencies = {"user", "social"})
package com.runtrack.course;
import com.runtrack.shared.access.Viewer;
