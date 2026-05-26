package com.game.agent.common;

import com.game.agent.common.context.TraceIdContext;
import java.util.List;

public record PagedResponse<T>(
        String code,
        String message,
        String traceId,
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PagedResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (size > 0) ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PagedResponse<>("0", "success", TraceIdContext.current(),
                content, page, size, totalElements, totalPages);
    }
}
