package com.c2.ew.util;

import com.c2.ew.domain.model.ErrorEllipse;
import com.c2.ew.domain.model.GeoPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Coğrafi ve Trigonometrik Hesaplama Yardımcısı
 * WGS-84 küresel projeksiyonu, LOB ışın uzatımı ve hata elipsi poligon üretimi.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final double METERS_PER_DEGREE_LAT = 111320.0;

    private GeoUtils() {}

    /**
     * İki coğrafi nokta arasındaki mesafeyi Haversine formülüyle metre cinsinden hesaplar.
     */
    public static double haversineDistanceMeters(GeoPoint p1, GeoPoint p2) {
        double dLat = Math.toRadians(p2.latitude() - p1.latitude());
        double dLon = Math.toRadians(p2.longitude() - p1.longitude());

        double lat1 = Math.toRadians(p1.latitude());
        double lat2 = Math.toRadians(p2.latitude());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Verilen başlangıç noktasından, belirli bir pusula açısında (bearing) ve mesafede varış noktasını hesaplar.
     */
    public static GeoPoint projectDestination(GeoPoint start, double bearingDegrees, double distanceMeters) {
        double lat1 = Math.toRadians(start.latitude());
        double lon1 = Math.toRadians(start.longitude());
        double brng = Math.toRadians(bearingDegrees);
        double dOverR = distanceMeters / EARTH_RADIUS_METERS;

        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(dOverR) +
                                Math.cos(lat1) * Math.sin(dOverR) * Math.cos(brng));

        double lon2 = lon1 + Math.atan2(Math.sin(brng) * Math.sin(dOverR) * Math.cos(lat1),
                                        Math.cos(dOverR) - Math.sin(lat1) * Math.sin(lat2));

        lon2 = (lon2 + 3 * Math.PI) % (2 * Math.PI) - Math.PI;

        return new GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2), start.altitudeMeters());
    }

    /**
     * Hata elipsini (semiMajor, semiMinor, orientation) haritada çizilebilmesi için kapalı bir GeoJSON poligon halkasına dönüştürür.
     */
    public static List<List<Double>> generateEllipsePolygon(GeoPoint center, ErrorEllipse ellipse, int steps) {
        List<List<Double>> ring = new ArrayList<>();
        double a = ellipse.semiMajorAxisMeters();
        double b = ellipse.semiMinorAxisMeters();
        double theta = Math.toRadians(ellipse.orientationDegrees());

        double centerLat = center.latitude();
        double centerLon = center.longitude();
        double metersPerDegreeLon = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(centerLat));

        for (int i = 0; i < steps; i++) {
            double angle = 2.0 * Math.PI * i / steps;

            double xLocal = a * Math.cos(angle);
            double yLocal = b * Math.sin(angle);

            double eastMeters = xLocal * Math.sin(theta) + yLocal * Math.cos(theta);
            double northMeters = xLocal * Math.cos(theta) - yLocal * Math.sin(theta);

            double lat = centerLat + (northMeters / METERS_PER_DEGREE_LAT);
            double lon = centerLon + (eastMeters / metersPerDegreeLon);

            ring.add(List.of(lon, lat));
        }

        if (!ring.isEmpty()) {
            ring.add(ring.get(0));
        }

        return ring;
    }
}
