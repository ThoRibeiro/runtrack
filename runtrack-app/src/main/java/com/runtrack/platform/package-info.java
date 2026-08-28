/**
 * Préoccupations techniques transverses : traduction des erreurs en {@code problem+json},
 * horloge et générateur aléatoire de l'application.
 *
 * <p>Déclaré module partagé plutôt que module fonctionnel : le découpage de premier niveau
 * est métier, et une couche technique n'y a pas sa place. Elle doit malgré tout vivre dans
 * un package que Spring Modulith connaît, sinon elle apparaîtrait comme un neuvième
 * domaine.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "platform")
package com.runtrack.platform;
