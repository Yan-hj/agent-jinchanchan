package com.game.agent.common.exception;

import com.game.agent.common.ErrorCode;

public class AgentException extends BusinessException {
    public AgentException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AgentException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }

    public AgentException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
