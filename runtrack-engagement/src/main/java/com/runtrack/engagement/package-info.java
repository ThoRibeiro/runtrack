/**
 * Likes et commentaires. On ne like et on ne commente que ce qu'on a le droit de voir,
 * décision déléguée à {@code CourseApi.canView}.
 *
 * <p>La dépendance vers {@code user} sert une seule chose : un commentaire a un auteur, et
 * une ligne de fil de commentaires montre son visage et son nom. Passer par {@code UserApi}
 * est ce que le §10 prévoit — l'alternative serait un client qui va chercher un profil par
 * ligne affichée.
 */
@org.springframework.modulith.ApplicationModule(displayName = "engagement",
        allowedDependencies = {"course", "user"})
package com.runtrack.engagement;
