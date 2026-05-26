package com.game.agent.common.exception;

import com.game.agent.common.ErrorCode;

public class IngestionException extends BusinessException {
    public IngestionException(ErrorCode errorCode) {
        super(errorCode);
    }

    public IngestionException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public IngestionException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
