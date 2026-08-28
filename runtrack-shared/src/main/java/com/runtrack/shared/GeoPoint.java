package com.runtrack.shared;

/** Une position géographique en degrés décimaux, WGS 84. */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            throw new IllegalArgumentException("Coordonnées non numériques");
        }
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude hors bornes : " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude hors bornes : " + longitude);
        }
    }
}
