package com.runtrack.course.internal.domain.activity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActivityTypeTest {

    /** Le seuil de vitesse qui sert au tri des points aberrants doit suivre l'activité. */
    @Test
    void ridesAreFasterThanRunsWhichAreFasterThanWalks() {
        assertThat(ActivityType.BIKE.maxPlausibleSpeedMetersPerSecond())
                .isGreaterThan(ActivityType.RUN.maxPlausibleSpeedMetersPerSecond());
        assertThat(ActivityType.RUN.maxPlausibleSpeedMetersPerSecond())
                .isGreaterThan(ActivityType.TRAIL.maxPlausibleSpeedMetersPerSecond());
        assertThat(ActivityType.TRAIL.maxPlausibleSpeedMetersPerSecond())
                .isGreaterThan(ActivityType.WALK.maxPlausibleSpeedMetersPerSecond());
    }

    /** À vitesse égale, courir coûte plus que pédaler, et le trail plus que la route. */
    @Test
    void energyCostFollowsTheActivity() {
        assertThat(ActivityType.TRAIL.metPerKilometerPerHour())
                .isGreaterThan(ActivityType.RUN.metPerKilometerPerHour());
        assertThat(ActivityType.RUN.metPerKilometerPerHour())
                .isGreaterThan(ActivityType.BIKE.metPerKilometerPerHour());
        assertThat(ActivityType.BIKE.metPerKilometerPerHour())
                .isGreaterThan(ActivityType.WALK.metPerKilometerPerHour());
    }
}
