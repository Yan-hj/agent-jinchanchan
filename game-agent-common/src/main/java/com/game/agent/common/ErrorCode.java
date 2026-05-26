package com.game.agent.common;

public enum ErrorCode {
    // Agent 运行时错误 (A0001 - A0999)
    AGENT_INTENT_FAILED("A0001", "Agent 意图识别失败", 500),
    AGENT_LLM_TIMEOUT("A0002", "LLM 响应超时", 500),

    // Agent 工具错误 (A1000 - A1999)
    TOOL_PARAM_INVALID("A1000", "工具调用参数校验失败", 400),
    TOOL_EXECUTION_TIMEOUT("A1001", "工具执行超时", 500),

    // 知识库错误 (K0001 - K0999)
    ES_CONNECTION_FAILED("K0001", "ES 连接失败", 500),
    ES_INDEX_MISMATCH("K0002", "索引不存在或维度不匹配", 500),

    // 检索错误 (K1000 - K1999)
    RETRIEVAL_NO_RESULTS("K1000", "检索无结果", 404),
    RERANK_FAILED("K1001", "Rerank 排序失败", 500),

    // 采集错误 (I0001 - I0999)
    INGEST_URL_UNREACHABLE("I0001", "URL 不可达或格式不正确", 400),
    INGEST_FORMAT_UNSUPPORTED("I0002", "文档格式不支持", 400),

    // 采集流水线错误 (I1000 - I1999)
    INGEST_EMBEDDING_FAILED("I1000", "Embedding 失败", 500),
    INGEST_ES_WRITE_FAILED("I1001", "ES 写入失败", 500),

    // 鉴权错误 (G0001 - G0999)
    AUTH_TOKEN_INVALID("G0001", "Token 过期或无效", 401),
    AUTH_PERMISSION_DENIED("G0002", "权限不足", 403),
    AUTH_RATE_LIMITED("G0003", "请求频率超限", 429),

    // 通用错误 (G1000 - G1999)
    PARAM_INVALID("G1000", "请求参数校验失败", 400),
    RESOURCE_NOT_FOUND("G1001", "资源不存在", 404),
    RATE_LIMIT_EXCEEDED("G1002", "请求频率超限", 429),

    // 系统内部错误 (Z0001)
    INTERNAL_ERROR("Z0001", "服务器内部错误", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    ErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
