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
