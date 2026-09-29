package com.c2.ew.domain.model;

/**
 * Kestirim Belirsizlik / Hata Elipsi
 */
public record ErrorEllipse(
    double semiMajorAxisMeters,
    double semiMinorAxisMeters,
    double orientationDegrees,
    Integer confidencePercent
) {}
