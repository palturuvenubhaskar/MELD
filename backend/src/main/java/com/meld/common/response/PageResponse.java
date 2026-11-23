package com.meld.common.response;

import java.util.List;
import java.util.function.Function;

/**
 * Stable pagination envelope for history and log endpoints (RULES.md Database:
 * paginate potentially large queries).
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <S, T> PageResponse<T> from(org.springframework.data.domain.Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
