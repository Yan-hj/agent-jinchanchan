package com.game.agent.memory.service;

import com.game.agent.memory.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.StringJoiner;

/**
 * 对话历史蒸馏服务。
 * <p>
 * 当会话消息数超过阈值（默认 10 条），将最早的一批未蒸馏消息发给 LLM 生成摘要，
 * 然后标记这些消息为 "已蒸馏"。后续 getHistory() 时，已蒸馏的消息替换为一条 SystemMessage(摘要)。
 * <p>
 * 作用：防止消息列表无限增长，保持 LLM 上下文窗口可控。
 * <p>
 * 注意：@ConditionalOnBean(ChatClient.Builder.class) — 没有 LLM 时不加载。
 */
@Service
@ConditionalOnBean(ChatClient.Builder.class)
public class DistillService {

    private static final Logger log = LoggerFactory.getLogger(DistillService.class);

    private static final String DISTILL_PROMPT = """
            你是一个对话摘要助手。请对以下游戏策略问答对话进行精简摘要，保留关键信息：
            用户问题、推荐的阵容/装备/运营思路、引用的版本号。
            摘要控制在 150 字以内，使用中文。
            """;

    private final ChatClient chatClient;
    private final MessageService messageService;
    private final int batchSize;

    public DistillService(
            ChatClient.Builder chatClientBuilder,
            MessageService messageService,
            @Value("${game-agent.memory.distill-batch:10}") int batchSize) {
        this.chatClient = chatClientBuilder.build();
        this.messageService = messageService;
        this.batchSize = batchSize;
    }

    /**
     * 对指定会话执行蒸馏：取最早 batchSize 条未蒸馏消息，调用 LLM 生成摘要。
     */
    public void distill(String sessionId) {
        List<Message> nonDistilled = messageService.getNonDistilledMessages(sessionId);
        if (nonDistilled.size() < batchSize) {
            return;
        }

        List<Message> batch = nonDistilled.subList(0, batchSize);
        String conversationText = formatConversation(batch);

        log.info("Distilling {} messages for session: {}", batch.size(), sessionId);

        String summary;
        try {
            summary = chatClient.prompt()
                    .system(DISTILL_PROMPT)
                    .user(conversationText)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Distill LLM call failed for session: {}", sessionId, e);
            summary = "（摘要生成失败）";
        }

        // 标记这 batchSize 条消息为已蒸馏，并写入摘要内容
        for (Message msg : batch) {
            messageService.markDistilled(msg.id(), summary);
        }
        log.info("Distilled {} messages for session: {}", batch.size(), sessionId);
    }

    private String formatConversation(List<Message> messages) {
        StringJoiner sj = new StringJoiner("\n");
        for (Message msg : messages) {
            String prefix = "user".equals(msg.role()) ? "用户" : "助手";
            sj.add(prefix + ": " + msg.text());
        }
        return sj.toString();
    }
}
