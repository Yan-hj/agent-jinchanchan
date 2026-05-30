package com.game.agent.core.multi;

import com.game.agent.knowledge.model.Citation;
import com.game.agent.render.model.Board;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

/**
 * 多 Agent 路由编排器。
 * <p>
 * 收到用户消息后，通过事件总线并行分发给多个子 Agent：
 * SearchAgent（检索知识库）→ StrategyAgent（生成策略）→ LineupAgent（渲染阵容图）
 * <p>
 * 子 Agent 通过 @EventListener + @Async 异步处理，结果通过 CompletableFuture 聚合。
 */
@Component
public class SupervisorAgent {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final AgentEventBus eventBus;
    private final ConcurrentHashMap<String, CompletableFuture<List<Citation>>> searchFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<String>> strategyFutures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<String>> lineupFutures = new ConcurrentHashMap<>();

    public SupervisorAgent(AgentEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 多 Agent 协作入口：触发所有子 Agent，等待结果聚合。
     * <p>
     * 流程：SearchAgent 检索 → StrategyAgent 生成策略 → LineupAgent 渲染
     */
    public MultiAgentResult process(String query, String seasonTag) {
        Instant start = Instant.now();
        String correlationId = java.util.UUID.randomUUID().toString().replace("-", "");
        log.info("SupervisorAgent processing: correlationId={}, query={}", correlationId, query);

        var searchFuture = new CompletableFuture<List<Citation>>();
        var strategyFuture = new CompletableFuture<String>();
        var lineupFuture = new CompletableFuture<String>();

        searchFutures.put(correlationId, searchFuture);
        strategyFutures.put(correlationId, strategyFuture);
        lineupFutures.put(correlationId, lineupFuture);

        eventBus.publish(new SearchEvent(correlationId, AgentRole.SUPERVISOR, query, seasonTag));

        try {
            List<Citation> citations = searchFuture.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            eventBus.publish(new StrategyEvent(correlationId, AgentRole.SUPERVISOR, query, seasonTag, citations));

            String strategy = strategyFuture.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            long took = Duration.between(start, Instant.now()).toMillis();
            log.info("SupervisorAgent completed in {}ms", took);
            return new MultiAgentResult(correlationId, strategy, citations, null, took);

        } catch (Exception e) {
            log.error("SupervisorAgent failed: correlationId={}", correlationId, e);
            return new MultiAgentResult(correlationId,
                    "处理失败: " + e.getMessage(), List.of(), null,
                    Duration.between(start, Instant.now()).toMillis());
        } finally {
            searchFutures.remove(correlationId);
            strategyFutures.remove(correlationId);
            lineupFutures.remove(correlationId);
        }
    }

    @EventListener
    public void onSearchResponse(SearchEvent event) {
        if (event.getType() != AgentEvent.AgentEventType.SEARCH_RESPONSE) return;
        CompletableFuture<List<Citation>> future = searchFutures.get(event.getCorrelationId());
        if (future != null) {
            future.complete(event.getResult().citations());
        }
    }

    @EventListener
    public void onStrategyResponse(StrategyEvent event) {
        if (event.getType() != AgentEvent.AgentEventType.STRATEGY_RESPONSE) return;
        CompletableFuture<String> future = strategyFutures.get(event.getCorrelationId());
        if (future != null) {
            future.complete(event.getResponse());
        }
    }

    @EventListener
    public void onLineupResponse(LineupEvent event) {
        if (event.getType() != AgentEvent.AgentEventType.LINEUP_RESPONSE) return;
        CompletableFuture<String> future = lineupFutures.get(event.getCorrelationId());
        if (future != null) {
            future.complete(event.getSvgContent());
        }
    }

    public record MultiAgentResult(
            String correlationId,
            String content,
            List<Citation> citations,
            String svgContent,
            long tookMs
    ) {}
}
