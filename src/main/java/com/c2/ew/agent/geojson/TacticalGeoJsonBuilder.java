package com.c2.ew.agent.geojson;

import com.c2.ew.agent.dto.TacticalComintSummaryDto;
import com.c2.ew.agent.dto.TacticalEmissionSummaryDto;
import com.c2.ew.domain.model.ErrorEllipse;
import com.c2.ew.domain.model.GeoPoint;
import com.c2.ew.util.GeoUtils;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TacticalGeoJsonBuilder {

    private static final double DEFAULT_LOB_LENGTH_METERS = 45_000.0;
    private static final int ELLIPSE_STEPS = 36;

    public Map<String, Object> buildEmissionsFeatureCollection(Collection<TacticalEmissionSummaryDto> emissions) {
        return buildUnifiedFeatureCollection(emissions, Collections.emptyList());
    }

    public Map<String, Object> buildComintFeatureCollection(Collection<TacticalComintSummaryDto> comints) {
        return buildUnifiedFeatureCollection(Collections.emptyList(), comints);
    }

    public Map<String, Object> buildUnifiedFeatureCollection(
        Collection<TacticalEmissionSummaryDto> emissions,
        Collection<TacticalComintSummaryDto> comints
    ) {
        List<Map<String, Object>> features = new ArrayList<>();

        if (emissions != null) {
            for (TacticalEmissionSummaryDto emi : emissions) {
                if (emi.yayinEnlem() != null && emi.yayinBoylam() != null) {
                    features.add(createEmissionPointFeature(emi));
                    if (emi.yayinSemiMajorMeters() != null && emi.yayinSemiMinorMeters() != null) {
                        features.add(createErrorEllipsePolygonFeature(emi));
                    }
                }
                if (emi.ehUnsuruEnlem() != null && emi.ehUnsuruBoylam() != null && emi.yon() != null) {
                    features.add(createBearingLineFeature(emi));
                }
            }
        }

        if (comints != null) {
            for (TacticalComintSummaryDto com : comints) {
                if (com.yayinEnlem() != null && com.yayinBoylam() != null) {
                    features.add(createComintPointFeature(com));
                    if (com.yayinSemiMajorMeters() != null && com.yayinSemiMinorMeters() != null) {
                        features.add(createComintErrorEllipsePolygonFeature(com));
                    }
                }
                if (com.ehUnsuruEnlem() != null && com.ehUnsuruBoylam() != null && com.yon() != null) {
                    features.add(createComintBearingLineFeature(com));
                }
            }
        }

        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("type", "FeatureCollection");
        collection.put("features", features);
        return collection;
    }

    private Map<String, Object> createEmissionPointFeature(TacticalEmissionSummaryDto emi) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("id", emi.id());

        Map<String, Object> geom = new LinkedHashMap<>();
        geom.put("type", "Point");
        geom.put("coordinates", List.of(emi.yayinBoylam(), emi.yayinEnlem()));
        feature.put("geometry", geom);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("layerType", "EMISSION_TARGET");
        props.put("sourceType", "RADAR");
        props.put("emissionId", emi.id());
        props.put("radarAdi", emi.radarAdi());
        props.put("teshisKimlik", emi.teshisKimlik());
        props.put("veriKaynagi", emi.veriKaynagi());
        props.put("hss", emi.hss());
        props.put("taciz", emi.taciz());
        props.put("etUygulamaDurumu", emi.etUygulamaDurumu());
        props.put("minFrekansMhz", emi.minFrekansMhz());
        props.put("maxFrekansMhz", emi.maxFrekansMhz());
        props.put("hedefYerBilgisi", emi.hedefYerBilgisi());
        feature.put("properties", props);

        return feature;
    }

    private Map<String, Object> createErrorEllipsePolygonFeature(TacticalEmissionSummaryDto emi) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("id", emi.id() + "-ellipse");

        GeoPoint center = new GeoPoint(emi.yayinEnlem(), emi.yayinBoylam());
        ErrorEllipse ellipse = new ErrorEllipse(
            emi.yayinSemiMajorMeters(),
            emi.yayinSemiMinorMeters(),
            emi.yayinOrientationDegrees() != null ? emi.yayinOrientationDegrees() : 0.0,
            95
        );

        List<List<Double>> ring = GeoUtils.generateEllipsePolygon(center, ellipse, ELLIPSE_STEPS);

        Map<String, Object> geom = new LinkedHashMap<>();
        geom.put("type", "Polygon");
        geom.put("coordinates", List.of(ring));
        feature.put("geometry", geom);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("layerType", "ERROR_ELLIPSE");
        props.put("sourceType", "RADAR");
        props.put("emissionId", emi.id());
        props.put("teshisKimlik", emi.teshisKimlik());
        feature.put("properties", props);

        return feature;
    }

    private Map<String, Object> createBearingLineFeature(TacticalEmissionSummaryDto emi) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("id", emi.id() + "-bearing");

        GeoPoint origin = new GeoPoint(emi.ehUnsuruEnlem(), emi.ehUnsuruBoylam());
        GeoPoint end = GeoUtils.projectDestination(origin, emi.yon(), DEFAULT_LOB_LENGTH_METERS);

        Map<String, Object> geom = new LinkedHashMap<>();
        geom.put("type", "LineString");
        geom.put("coordinates", List.of(
            List.of(origin.longitude(), origin.latitude()),
            List.of(end.longitude(), end.latitude())
        ));
        feature.put("geometry", geom);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("layerType", "BEARING_RAY");
        props.put("sourceType", "RADAR");
        props.put("emissionId", emi.id());
        props.put("ehUnsuru", emi.ehUnsuru());
        props.put("bearingDegrees", emi.yon());
        feature.put("properties", props);

        return feature;
    }

    // COMINT Features
    private Map<String, Object> createComintPointFeature(TacticalComintSummaryDto com) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("id", com.id());

        Map<String, Object> geom = new LinkedHashMap<>();
        geom.put("type", "Point");
        geom.put("coordinates", List.of(com.yayinBoylam(), com.yayinEnlem()));
        feature.put("geometry", geom);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("layerType", "COMINT_TARGET");
        props.put("sourceType", "COMINT");
        props.put("comintId", com.id());
        props.put("cagriAdi", com.cagriAdi());
        props.put("karsiCagriAdi", com.karsiCagriAdi());
        props.put("lisan", com.lisan());
        props.put("tip", com.tip());
        props.put("haberlesmeSekli", com.haberlesmeSekli());
        props.put("protokol", com.protokol());
        props.put("modulasyon", com.modulasyon());
        props.put("mti", com.mti());
        props.put("teshisKimlik", com.teshisKimlik());
        props.put("veriKaynagi", com.veriKaynagi());
        props.put("minFrekansMhz", com.minFrekansMhz());
        props.put("maxFrekansMhz", com.maxFrekansMhz());
        props.put("genlikDbm", com.genlikDbm());
        props.put("hedefYerBilgisi", com.hedefYerBilgisi());
        feature.put("properties", props);

        return feature;
    }

    private Map<String, Object> createComintErrorEllipsePolygonFeature(TacticalComintSummaryDto com) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("id", com.id() + "-ellipse");

        GeoPoint center = new GeoPoint(com.yayinEnlem(), com.yayinBoylam());
        ErrorEllipse ellipse = new ErrorEllipse(
            com.yayinSemiMajorMeters(),
            com.yayinSemiMinorMeters(),
            com.yayinOrientationDegrees() != null ? com.yayinOrientationDegrees() : 0.0,
            95
        );

        List<List<Double>> ring = GeoUtils.generateEllipsePolygon(center, ellipse, ELLIPSE_STEPS);

        Map<String, Object> geom = new LinkedHashMap<>();
        geom.put("type", "Polygon");
        geom.put("coordinates", List.of(ring));
        feature.put("geometry", geom);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("layerType", "COMINT_ERROR_ELLIPSE");
        props.put("sourceType", "COMINT");
        props.put("comintId", com.id());
        props.put("teshisKimlik", com.teshisKimlik());
        feature.put("properties", props);

        return feature;
    }

    private Map<String, Object> createComintBearingLineFeature(TacticalComintSummaryDto com) {
        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("id", com.id() + "-bearing");

        GeoPoint origin = new GeoPoint(com.ehUnsuruEnlem(), com.ehUnsuruBoylam());
        GeoPoint end = GeoUtils.projectDestination(origin, com.yon(), DEFAULT_LOB_LENGTH_METERS);

        Map<String, Object> geom = new LinkedHashMap<>();
        geom.put("type", "LineString");
        geom.put("coordinates", List.of(
            List.of(origin.longitude(), origin.latitude()),
            List.of(end.longitude(), end.latitude())
        ));
        feature.put("geometry", geom);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("layerType", "COMINT_BEARING_RAY");
        props.put("sourceType", "COMINT");
        props.put("comintId", com.id());
        props.put("ehUnsuru", com.goreviIcraEdenEhUnsuru());
        props.put("bearingDegrees", com.yon());
        feature.put("properties", props);

        return feature;
    }
}
