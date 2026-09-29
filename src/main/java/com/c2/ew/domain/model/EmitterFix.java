package com.c2.ew.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Füzyonlanmış Kestirim / Radar Elipsi
 */
public record EmitterFix(
    String fixId,
    Instant firstSeen,
    Instant lastSeen,
    GeoPoint estimatedLocation,
    ErrorEllipse errorEllipse,
    RfSignature rfSignature,
    List<String> sourceLobIds,
    EmitterActivity status,
    String radarType,
    String platformType
) {
    public EmitterFix(
        String fixId,
        Instant firstSeen,
        Instant lastSeen,
        GeoPoint estimatedLocation,
        ErrorEllipse errorEllipse,
        RfSignature rfSignature,
        List<String> sourceLobIds,
        EmitterActivity status
    ) {
        this(fixId, firstSeen, lastSeen, estimatedLocation, errorEllipse, rfSignature, sourceLobIds, status, "Bilinmeyen Radar", "LAND_MOBILE");
    }
}
