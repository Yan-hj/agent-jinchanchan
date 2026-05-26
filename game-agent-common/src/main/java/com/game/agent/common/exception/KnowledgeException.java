package com.game.agent.common.exception;

import com.game.agent.common.ErrorCode;

public class KnowledgeException extends BusinessException {
    public KnowledgeException(ErrorCode errorCode) {
        super(errorCode);
    }

    public KnowledgeException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public KnowledgeException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
