package com.c2.ew.agent.dto;

/**
 * Taktik Copilot Kullanıcı Sorgusu DTO'su (Çoklu Tur Sohbet Hafızası Destekli)
 */
public record TacticalQueryRequest(
    String userPrompt,
    String conversationId,
    Double centerLat,
    Double centerLon,
    Double radiusKm
) {
    public TacticalQueryRequest(String userPrompt, Double centerLat, Double centerLon, Double radiusKm) {
        this(userPrompt, "c2-default-session", centerLat, centerLon, radiusKm);
    }
}
