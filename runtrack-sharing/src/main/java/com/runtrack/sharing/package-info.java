/**
 * Liens de partage publics, révocables et à durée de vie limitée. Sens unique :
 * {@code sharing} appelle {@code CourseApi}, jamais l'inverse.
 */
@org.springframework.modulith.ApplicationModule(displayName = "sharing",
        allowedDependencies = {"course"})
package com.runtrack.sharing;
