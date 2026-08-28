/**
 * Noyau partagé : identifiants, objets valeur, erreurs métier, {@code Viewer}, contexte
 * d'appel et horloge. Ne dépend d'aucun autre module et n'héberge aucune règle de domaine.
 *
 * <p>Module ouvert : tous ses sous-packages sont accessibles. Imposer une interface nommée
 * pour chaque objet valeur du noyau ne protégerait rien et alourdirait tout.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN, displayName = "shared")
package com.runtrack.shared;
import com.runtrack.shared.access.Viewer;
