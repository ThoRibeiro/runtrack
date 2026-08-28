package com.runtrack.course.internal.domain.activity;

/**
 * La nature d'une course. Chaque type porte les seuils qui n'ont de sens que pour lui :
 * un point à 15 m/s est aberrant à pied et banal à vélo.
 */
public enum ActivityType {

    RUN(8.5, 1.0),
    TRAIL(6.5, 1.15),
    BIKE(25.0, 0.5),
    WALK(3.5, 0.4);

    private final double maxPlausibleSpeed;
    private final double metPerKilometerPerHour;

    ActivityType(double maxPlausibleSpeed, double metPerKilometerPerHour) {
        this.maxPlausibleSpeed = maxPlausibleSpeed;
        this.metPerKilometerPerHour = metPerKilometerPerHour;
    }

    /**
     * Au-delà, le point vient d'un saut GPS, pas d'un déplacement. Les valeurs sont
     * larges à dessein : mieux vaut garder un point douteux que jeter le sprint final.
     */
    public double maxPlausibleSpeedMetersPerSecond() {
        return maxPlausibleSpeed;
    }

    /**
     * Le coefficient reliant la vitesse au MET, pour l'estimation de calories. Approximation
     * courante en physiologie de l'effort : courir à 10 km/h vaut environ 10 MET.
     */
    public double metPerKilometerPerHour() {
        return metPerKilometerPerHour;
    }
}
