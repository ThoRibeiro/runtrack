/**
 * Likes et commentaires. On ne like et on ne commente que ce qu'on a le droit de voir,
 * décision déléguée à {@code CourseApi.canView}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "engagement",
        allowedDependencies = {"course"})
package com.runtrack.engagement;
