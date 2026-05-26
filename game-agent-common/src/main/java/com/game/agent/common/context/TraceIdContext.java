package com.game.agent.common.context;

import java.util.UUID;

public final class TraceIdContext {
    private static final ThreadLocal<String> CONTEXT = ThreadLocal.withInitial(
            () -> UUID.randomUUID().toString().replace("-", "")
    );

    private TraceIdContext() {}

    public static void set(String traceId) {
        CONTEXT.set(traceId);
    }

    public static String current() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
