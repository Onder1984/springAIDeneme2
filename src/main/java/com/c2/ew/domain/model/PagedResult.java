package com.c2.ew.domain.model;

import java.util.List;

/**
 * Büyük veri sorgularında LLM context sınırını korumak ve güvenli sayfalama sağlamak için sarmalayıcı DTO.
 */
public record PagedResult<T>(
    List<T> items,
    int page,
    int pageSize,
    long totalCount,
    int totalPages,
    boolean hasMore,
    String note
) {
    public static <T> PagedResult<T> of(List<T> items, int page, int pageSize, long totalCount, String note) {
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalCount / pageSize) : 1;
        boolean hasMore = (page + 1) * pageSize < totalCount;
        return new PagedResult<>(items, page, pageSize, totalCount, totalPages, hasMore, note);
    }
}
