package com.c2.ew.domain.model;

/**
 * RF Sinyal Karakteristiği
 */
public record RfSignature(
    double frequencyMhz,
    Double pulseWidthUs,
    Double priUs,
    String band,
    String modulation
) {}
