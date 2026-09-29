package com.c2.ew.agent.dto;

/**
 * Sayfalanmış sorgular için UI ve LLM metaveri bilgisi.
 */
public record PaginationMetadata(
    boolean hasMore,
    int currentPage,
    int totalPages,
    long totalCount,
    int pageSize,
    String targetType,
    String nextPrompt
) {}
