package com.game.agent.core;

import com.game.agent.common.metadata.DocumentMetadata;
import com.game.agent.core.model.AgentResponse;
import com.game.agent.knowledge.model.Citation;
import com.game.agent.knowledge.model.RetrievalResult;
import com.game.agent.knowledge.service.KnowledgeRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.StringJoiner;

@Service
public class GameAgent {

    private static final Logger log = LoggerFactory.getLogger(GameAgent.class);

    private final ChatClient chatClient;
    private final KnowledgeRetrievalService retrievalService;

    public GameAgent(ChatClient gameAgentChatClient, KnowledgeRetrievalService retrievalService) {
        this.chatClient = gameAgentChatClient;
        this.retrievalService = retrievalService;
    }

    public AgentResponse chat(String message) {
        return chat(message, null);
    }

    public AgentResponse chat(String message, String seasonTag) {
        long start = System.currentTimeMillis();
        log.info("Agent chat: seasonTag={}", seasonTag);

        String filter = seasonTag != null
                ? DocumentMetadata.VERSION_TAG + " == '" + seasonTag + "'"
                : null;

        RetrievalResult ctx = retrievalService.retrieve(message, filter);
        log.info("Pre-retrieved {} documents in {}ms", ctx.citations().size(), ctx.tookMs());

        String contextPrompt = buildContextPrompt(ctx.citations());

        String answer = chatClient.prompt()
                .system(s -> s.text(contextPrompt))
                .user(message)
                .call()
                .content();

        long took = System.currentTimeMillis() - start;
        log.info("Agent responded in {}ms, citations={}", took, ctx.citations().size());

        return new AgentResponse(answer, ctx.citations());
    }

    public AgentResponse chatWithHistory(String message, String seasonTag,
                                          List<org.springframework.ai.chat.messages.Message> history) {
        long start = System.currentTimeMillis();
        log.info("Agent chat with history: seasonTag={}, historySize={}", seasonTag, history.size());

        String filter = seasonTag != null
                ? DocumentMetadata.VERSION_TAG + " == '" + seasonTag + "'"
                : null;

        RetrievalResult ctx = retrievalService.retrieve(message, filter);
        log.info("Pre-retrieved {} documents in {}ms", ctx.citations().size(), ctx.tookMs());

        String contextPrompt = buildContextPrompt(ctx.citations());

        String answer = chatClient.prompt()
                .messages(history)
                .system(s -> s.text(contextPrompt))
                .user(message)
                .call()
                .content();

        long took = System.currentTimeMillis() - start;
        log.info("Agent responded in {}ms, citations={}", took, ctx.citations().size());

        return new AgentResponse(answer, ctx.citations());
    }

    public Flux<String> chatStream(String message, String seasonTag) {
        long start = System.currentTimeMillis();
        log.info("Agent chat stream: seasonTag={}", seasonTag);

        String filter = seasonTag != null
                ? DocumentMetadata.VERSION_TAG + " == '" + seasonTag + "'"
                : null;

        RetrievalResult ctx = retrievalService.retrieve(message, filter);
        log.info("Pre-retrieved {} documents in {}ms", ctx.citations().size(), ctx.tookMs());

        String contextPrompt = buildContextPrompt(ctx.citations());

        return chatClient.prompt()
                .system(s -> s.text(contextPrompt))
                .user(message)
                .stream()
                .content()
                .doOnComplete(() -> {
                    long took = System.currentTimeMillis() - start;
                    log.info("Agent stream completed in {}ms, citations={}", took, ctx.citations().size());
                });
    }

    private String buildContextPrompt(List<Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return "当前没有检索到相关参考知识，请根据你的训练数据回答，并说明信息来源的局限性。";
        }

        StringJoiner sj = new StringJoiner("\n\n");
        sj.add("以下是从知识库检索到的相关参考信息，请基于这些内容回答问题：\n");
        for (int i = 0; i < citations.size(); i++) {
            Citation c = citations.get(i);
            sj.add("[" + (i + 1) + "] " + c.title() + " (来源: " + c.source() + ")\n"
                    + c.snippet());
        }
        sj.add("回答时请在引用处标注角标编号，如「根据官方公告[1]」。"
                + "如果检索信息不足以回答用户问题，可以使用提供的工具进行定向搜索。");
        return sj.toString();
    }
}
