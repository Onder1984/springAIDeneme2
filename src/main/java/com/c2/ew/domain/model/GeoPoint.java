package com.c2.ew.domain.model;

/**
 * Temel WGS-84 Konumu
 */
public record GeoPoint(
    double latitude,
    double longitude,
    Double altitudeMeters
) {
    public GeoPoint(double latitude, double longitude) {
        this(latitude, longitude, 0.0);
    }
}
