package com.game.agent.common;

import com.game.agent.common.context.TraceIdContext;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId
) {
    private static final String SUCCESS_CODE = "0";
    private static final String SUCCESS_MESSAGE = "success";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data, TraceIdContext.current());
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), null, TraceIdContext.current());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String detail) {
        return new ApiResponse<>(errorCode.code(), detail, null, TraceIdContext.current());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(code, message, null, TraceIdContext.current());
    }
}
