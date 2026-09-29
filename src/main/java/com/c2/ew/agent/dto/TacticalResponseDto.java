package com.c2.ew.agent.dto;

import java.util.List;
import java.util.Map;

/**
 * Taktik Operasyonel Yanıt DTO'su (Çift Çıktı Modeli + LLM Telemetri Logları + Sayfalama)
 */
public record TacticalResponseDto(
    String operationalBriefing,
    Map<String, Object> tacticalGeoJson,
    List<String> executionLogs,
    PaginationMetadata pagination
) {
    public TacticalResponseDto(String operationalBriefing, Map<String, Object> tacticalGeoJson, List<String> executionLogs) {
        this(operationalBriefing, tacticalGeoJson, executionLogs, null);
    }

    public TacticalResponseDto(String operationalBriefing, Map<String, Object> tacticalGeoJson) {
        this(operationalBriefing, tacticalGeoJson, List.of(), null);
    }
}
