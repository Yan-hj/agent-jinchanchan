package com.game.agent.common.exception;

import com.game.agent.common.ErrorCode;

public class RateLimitException extends BusinessException {
    public RateLimitException() {
        super(ErrorCode.AUTH_RATE_LIMITED);
    }

    public RateLimitException(String detail) {
        super(ErrorCode.AUTH_RATE_LIMITED, detail);
    }
}
