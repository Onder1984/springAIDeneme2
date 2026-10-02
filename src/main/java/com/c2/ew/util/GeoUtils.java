package com.c2.ew.util;

import com.c2.ew.domain.model.ErrorEllipse;
import com.c2.ew.domain.model.GeoPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Coğrafi ve Trigonometrik Hesaplama Yardımcısı
 * WGS-84 küresel projeksiyonu, LOB ışın uzatımı, hata elipsi poligon üretimi ve
 * iki hata elipsi arasındaki örtüşme (overlap) / çeper mesafesi analizi.
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
     * Başlangıç noktasından hedef noktaya doğru kerteriz açısını (0-360 derece) hesaplar.
     */
    public static double calculateBearingDegrees(GeoPoint p1, GeoPoint p2) {
        double lat1 = Math.toRadians(p1.latitude());
        double lon1 = Math.toRadians(p1.longitude());
        double lat2 = Math.toRadians(p2.latitude());
        double lon2 = Math.toRadians(p2.longitude());

        double dLon = lon2 - lon1;
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);

        double brng = Math.toDegrees(Math.atan2(y, x));
        return (brng + 360.0) % 360.0;
    }

    /**
     * Verilen elipsin, belirli bir kerteriz açısındaki merkezden çepere olan yarıçapını (radial radius) hesaplar.
     */
    public static double calculateEllipseRadiusMeters(double semiMajor, double semiMinor, double orientationDegrees, double targetBearingDegrees) {
        double a = semiMajor > 0 ? semiMajor : 500.0;
        double b = semiMinor > 0 ? semiMinor : 250.0;

        double phi = Math.toRadians(targetBearingDegrees - orientationDegrees);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);

        double denom = Math.sqrt((b * cosPhi) * (b * cosPhi) + (a * sinPhi) * (a * sinPhi));
        if (denom == 0) return b;
        return (a * b) / denom;
    }

    /**
     * İki hata elipsi arasındaki mekânsal ilişki kaydı.
     */
    public record EllipseSpatialRelation(
        double centerDistanceMeters,
        double boundaryDistanceMeters,
        boolean isOverlapping,
        double overlapMeters,
        double overlapPercentage,
        double mahalanobisDistanceSq,
        boolean isStatisticallyCoLocated95
    ) {}

    /**
     * İki hata elipsi arasındaki örtüşme (overlap), çeperden çepere mesafe ve Mahalanobis füzyonunu hesaplar.
     */
    public static EllipseSpatialRelation calculateEllipseSpatialRelation(
        GeoPoint p1, double a1, double b1, double orient1,
        GeoPoint p2, double a2, double b2, double orient2
    ) {
        double centerDist = haversineDistanceMeters(p1, p2);

        double brng12 = calculateBearingDegrees(p1, p2);
        double brng21 = (brng12 + 180.0) % 360.0;

        double r1 = calculateEllipseRadiusMeters(a1, b1, orient1, brng12);
        double r2 = calculateEllipseRadiusMeters(a2, b2, orient2, brng21);
        double rSum = r1 + r2;

        boolean isOverlapping = centerDist <= rSum;
        double boundaryDist;
        double overlapMeters;
        double overlapPct;

        if (isOverlapping) {
            boundaryDist = 0.0;
            overlapMeters = rSum - centerDist;
            double minDiameter = Math.min(r1, r2) * 2.0;
            overlapPct = minDiameter > 0 ? Math.min(100.0, (overlapMeters / minDiameter) * 100.0) : 100.0;
        } else {
            boundaryDist = centerDist - rSum;
            overlapMeters = 0.0;
            overlapPct = 0.0;
        }

        // 2D Gauss Kovaryans ve Mahalanobis Hesabı (Local Tangent Plane)
        double meanLat = (p1.latitude() + p2.latitude()) / 2.0;
        double metersPerDegLon = METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(meanLat));
        double dEast = (p2.longitude() - p1.longitude()) * metersPerDegLon;
        double dNorth = (p2.latitude() - p1.latitude()) * METERS_PER_DEGREE_LAT;

        double rad1 = Math.toRadians(orient1);
        double s1 = Math.sin(rad1);
        double c1 = Math.cos(rad1);
        double c1_11 = (a1 * a1) * (s1 * s1) + (b1 * b1) * (c1 * c1);
        double c1_22 = (a1 * a1) * (c1 * c1) + (b1 * b1) * (s1 * s1);
        double c1_12 = (a1 * a1 - b1 * b1) * s1 * c1;

        double rad2 = Math.toRadians(orient2);
        double s2 = Math.sin(rad2);
        double c2 = Math.cos(rad2);
        double c2_11 = (a2 * a2) * (s2 * s2) + (b2 * b2) * (c2 * c2);
        double c2_22 = (a2 * a2) * (c2 * c2) + (b2 * b2) * (s2 * s2);
        double c2_12 = (a2 * a2 - b2 * b2) * s2 * c2;

        double s11 = c1_11 + c2_11;
        double s22 = c1_22 + c2_22;
        double s12 = c1_12 + c2_12;
        double det = s11 * s22 - s12 * s12;

        double dMSq = 999.0;
        if (det > 0.0001) {
            double inv11 = s22 / det;
            double inv22 = s11 / det;
            double inv12 = -s12 / det;
            dMSq = dEast * (inv11 * dEast + inv12 * dNorth) + dNorth * (inv12 * dEast + inv22 * dNorth);
        }

        boolean isCoLocated = dMSq <= 5.991; // Chi-Square df=2 %95 güven eşiği

        return new EllipseSpatialRelation(
            centerDist,
            boundaryDist,
            isOverlapping,
            overlapMeters,
            overlapPct,
            dMSq,
            isCoLocated
        );
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
