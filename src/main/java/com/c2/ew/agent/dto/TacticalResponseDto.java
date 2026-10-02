package com.c2.ew.agent.dto;

import java.util.List;

/**
 * Taktik Operasyonel Yanıt DTO'su (Metinsel Taktik Brifing + LLM Telemetri Logları + Sayfalama)
 */
public record TacticalResponseDto(
    String operationalBriefing,
    List<String> executionLogs,
    PaginationMetadata pagination
) {
    public TacticalResponseDto(String operationalBriefing, List<String> executionLogs) {
        this(operationalBriefing, executionLogs, null);
    }

    public TacticalResponseDto(String operationalBriefing) {
        this(operationalBriefing, List.of(), null);
    }
}
