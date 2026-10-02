package com.c2.ew.agent.dto;

/**
 * Taktik Copilot Kullanıcı Sorgusu DTO'su (Çoklu Tur Sohbet Hafızası Destekli)
 */
public record TacticalQueryRequest(
    String userPrompt,
    String conversationId
) {
    public TacticalQueryRequest(String userPrompt) {
        this(userPrompt, "c2-tactical-session");
    }
}
