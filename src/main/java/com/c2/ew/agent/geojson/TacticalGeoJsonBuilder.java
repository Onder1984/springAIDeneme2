package com.c2.ew.agent.geojson;

import com.c2.ew.domain.model.EmitterFix;
import com.c2.ew.domain.model.EmitterLob;
import com.c2.ew.domain.model.GeoPoint;
import com.c2.ew.util.GeoUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministik GeoJSON Oluşturucu
 * EmitterFix kestirimlerini ve EmitterLob yön hatlarını MapLibre GL JS uyumlu
 * standart GeoJSON FeatureCollection veri yapısına dönüştürür.
 */
@Component
public class TacticalGeoJsonBuilder {

    private static final double DEFAULT_LOB_LENGTH_METERS = 50_000.0;
    private static final int ELLIPSE_STEPS = 36;

    public Map<String, Object> buildTacticalFeatureCollection(
        Collection<EmitterFix> fixes,
        Collection<EmitterLob> lobs,
        Set<String> orphanLobIds
    ) {
        List<Map<String, Object>> features = new ArrayList<>();

        if (fixes != null) {
            for (EmitterFix fix : fixes) {
                features.add(createFixPointFeature(fix));
                if (fix.errorEllipse() != null) {
                    features.add(createErrorEllipsePolygonFeature(fix));
                }
            }
        }

        if (lobs != null) {
            Set<String> renderedSensors = new HashSet<>();
            for (EmitterLob lob : lobs) {
                boolean isOrphan = orphanLobIds != null && orphanLobIds.contains(lob.lobId());
                features.add(createLobLineFeature(lob, isOrphan));

                if (lob.sensorPosition() != null && renderedSensors.add(lob.sensorNodeId())) {
                    features.add(createSensorPointFeature(lob.sensorNodeId(), lob.sensorPosition()));
                }
            }
        }

        Map<String, Object> featureCollection = new LinkedHashMap<>();
        featureCollection.put("type", "FeatureCollection");
        featureCollection.put("features", features);
        return featureCollection;
    }

    private Map<String, Object> createFixPointFeature(EmitterFix fix) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");

        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "Point");
        geometry.put("coordinates", List.of(
            fix.estimatedLocation().longitude(),
            fix.estimatedLocation().latitude()
        ));
        feature.put("geometry", geometry);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("featureClass", "EMITTER_FIX");
        props.put("id", fix.fixId());
        props.put("status", fix.status().name());
        props.put("band", fix.rfSignature() != null ? fix.rfSignature().band() : "UNKNOWN");
        props.put("frequencyMhz", fix.rfSignature() != null ? fix.rfSignature().frequencyMhz() : null);
        props.put("pulseWidthUs", fix.rfSignature() != null ? fix.rfSignature().pulseWidthUs() : null);
        props.put("priUs", fix.rfSignature() != null ? fix.rfSignature().priUs() : null);
        props.put("semiMajorAxisMeters", fix.errorEllipse() != null ? fix.errorEllipse().semiMajorAxisMeters() : 0.0);
        props.put("semiMinorAxisMeters", fix.errorEllipse() != null ? fix.errorEllipse().semiMinorAxisMeters() : 0.0);
        props.put("orientationDegrees", fix.errorEllipse() != null ? fix.errorEllipse().orientationDegrees() : 0.0);
        props.put("confidencePercent", fix.errorEllipse() != null ? fix.errorEllipse().confidencePercent() : 0);
        props.put("sourceLobCount", fix.sourceLobIds() != null ? fix.sourceLobIds().size() : 0);
        props.put("accuracyClass", (fix.errorEllipse() != null && fix.errorEllipse().semiMajorAxisMeters() > 5000.0) ? "LOW" : "HIGH");
        props.put("lastSeen", fix.lastSeen().toString());

        feature.put("properties", props);
        return feature;
    }

    private Map<String, Object> createErrorEllipsePolygonFeature(EmitterFix fix) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");

        List<List<Double>> ring = GeoUtils.generateEllipsePolygon(
            fix.estimatedLocation(),
            fix.errorEllipse(),
            ELLIPSE_STEPS
        );

        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "Polygon");
        geometry.put("coordinates", List.of(ring));
        feature.put("geometry", geometry);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("featureClass", "ERROR_ELLIPSE");
        props.put("fixId", fix.fixId());
        props.put("status", fix.status().name());
        props.put("accuracyClass", (fix.errorEllipse().semiMajorAxisMeters() > 5000.0) ? "LOW" : "HIGH");
        props.put("semiMajorAxisMeters", fix.errorEllipse().semiMajorAxisMeters());
        feature.put("properties", props);

        return feature;
    }

    private Map<String, Object> createLobLineFeature(EmitterLob lob, boolean isOrphan) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");

        GeoPoint start = lob.sensorPosition();
        GeoPoint end = GeoUtils.projectDestination(start, lob.bearingDegrees(), DEFAULT_LOB_LENGTH_METERS);

        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "LineString");
        geometry.put("coordinates", List.of(
            List.of(start.longitude(), start.latitude()),
            List.of(end.longitude(), end.latitude())
        ));
        feature.put("geometry", geometry);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("featureClass", "EMITTER_LOB");
        props.put("id", lob.lobId());
        props.put("sensorNodeId", lob.sensorNodeId());
        props.put("bearingDegrees", lob.bearingDegrees());
        props.put("angularAccuracyDeg", lob.angularAccuracyDeg());
        props.put("band", lob.rfSignature() != null ? lob.rfSignature().band() : "UNKNOWN");
        props.put("frequencyMhz", lob.rfSignature() != null ? lob.rfSignature().frequencyMhz() : null);
        props.put("isOrphan", isOrphan);
        props.put("timestamp", lob.timestamp().toString());

        feature.put("properties", props);
        return feature;
    }

    private Map<String, Object> createSensorPointFeature(String sensorNodeId, GeoPoint position) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");

        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "Point");
        geometry.put("coordinates", List.of(position.longitude(), position.latitude()));
        feature.put("geometry", geometry);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("featureClass", "SENSOR_NODE");
        props.put("id", sensorNodeId);
        feature.put("properties", props);

        return feature;
    }
}
