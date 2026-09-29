package com.c2.ew.domain.model;

import java.time.Instant;

/**
 * Ham Yön Bulma Hattı (Line of Bearing - LOB)
 */
public record EmitterLob(
    String lobId,
    String sensorNodeId,
    Instant timestamp,
    GeoPoint sensorPosition,
    double bearingDegrees,
    double angularAccuracyDeg,
    RfSignature rfSignature
) {}
