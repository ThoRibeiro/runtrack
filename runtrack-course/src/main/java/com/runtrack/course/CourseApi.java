package com.runtrack.course;
import com.runtrack.shared.access.Viewer;
import com.runtrack.shared.id.ActivityId;

/**
 * Point d'entrée unique du module {@code course} pour les autres modules.
 *
 * <p>Signatures prévues au lot 5, dont {@code canView(Viewer, ActivityId)} : la seule
 * porte d'entrée de l'autorisation de lecture, qu'aucun contrôleur ne réimplémente.
 */
public interface CourseApi {
}
