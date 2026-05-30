package com.game.agent.core.multi;

import com.game.agent.knowledge.model.Citation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

@Component
public class StrategyAgent {

    private static final Logger log = LoggerFactory.getLogger(StrategyAgent.class);

    private final ChatClient chatClient;
    private final AgentEventBus eventBus;

    public StrategyAgent(ChatClient gameAgentChatClient, AgentEventBus eventBus) {
        this.chatClient = gameAgentChatClient;
        this.eventBus = eventBus;
    }

    @Async
    @EventListener
    public void handleStrategyRequest(StrategyEvent event) {
        if (event.getType() != AgentEvent.AgentEventType.STRATEGY_REQUEST) return;

        log.info("StrategyAgent received: correlationId={}, query={}",
                event.getCorrelationId(), event.getQuery());

        String contextPrompt = buildContextPrompt(event.getContextCitations());

        String response = chatClient.prompt()
                .system(s -> s.text(contextPrompt))
                .user(event.getQuery())
                .call()
                .content();

        eventBus.publish(new StrategyEvent(
                event.getCorrelationId(), AgentRole.STRATEGY, response));

        log.info("StrategyAgent completed: correlationId={}", event.getCorrelationId());
    }

    private String buildContextPrompt(List<Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return "当前没有检索到相关参考知识，请根据你的训练数据回答，并说明信息来源的局限性。";
        }
        StringJoiner sj = new StringJoiner("\n\n");
        sj.add("以下是从知识库检索到的相关参考信息：\n");
        for (int i = 0; i < citations.size(); i++) {
            Citation c = citations.get(i);
            sj.add("[" + (i + 1) + "] " + c.title() + " (来源: " + c.source() + ")\n" + c.snippet());
        }
        sj.add("回答时请标注引用编号，如「根据官方公告[1]」。");
        return sj.toString();
    }
}
