/**
 * Le domaine riche : cycle de vie d'une course, ingestion GPS, statistiques, live.
 * Ne connaît ni {@code sharing} (le {@code Viewer} vient de {@code shared}) ni
 * {@code engagement} : c'est ce qui garde le graphe acyclique.
 */
@org.springframework.modulith.ApplicationModule(displayName = "course",
        allowedDependencies = {"user"})
package com.runtrack.course;
