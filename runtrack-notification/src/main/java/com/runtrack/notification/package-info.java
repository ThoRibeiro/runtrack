/**
 * Notifications in-app, push mobile et préférences. Aucun module ne dépend de lui ; en
 * sortie il appelle bien {@code UserApi}, {@code SocialApi} et {@code CourseApi}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "notification",
        allowedDependencies = {"user", "social", "course",
                               "course :: events", "social :: events", "engagement :: events"})
package com.runtrack.notification;
