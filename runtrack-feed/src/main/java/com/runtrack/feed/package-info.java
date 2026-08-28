/**
 * Projection de lecture du fil d'actualité. Module ajouté au lot 1 : porter le feed dans
 * {@code course} imposait {@code course → engagement} pour les compteurs alors que
 * {@code engagement → course} existe déjà, donc un cycle. Ici personne ne dépend de
 * {@code feed}, et il compose librement.
 */
@org.springframework.modulith.ApplicationModule(displayName = "feed",
        allowedDependencies = {"user", "social", "course",
                               "course :: events", "engagement :: events"})
package com.runtrack.feed;
