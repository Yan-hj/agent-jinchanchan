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

/**
 * Agent 核心编排服务。
 * <p>
 * 职责：接收用户问题 → 从知识库检索上下文 → 调用 LLM（带 tools）→ 返回答案 + 引用来源。
 * 整个流程是同步的 chat() 和流式的 chatStream() 两套入口。
 * <p>
 * 工作流程：
 * 1. pre-retrieval：通过 KnowledgeRetrievalService 从 ES 向量库检索相关文档
 * 2. context 组装：把检索结果拼入 system prompt，让 LLM 基于事实回答
 * 3. LLM 调用：ChatClient 自动处理 tool-calling loop（LLM 可调 searchOfficialMeta 等工具深挖）
 * 4. 返回：AgentResponse(content, citations)
 */
@Service
public class GameAgent {

    private static final Logger log = LoggerFactory.getLogger(GameAgent.class);

    private final ChatClient chatClient;
    private final KnowledgeRetrievalService retrievalService;

    public GameAgent(ChatClient gameAgentChatClient, KnowledgeRetrievalService retrievalService) {
        this.chatClient = gameAgentChatClient;
        this.retrievalService = retrievalService;
    }

    /**
     * 最简单的单轮问答，不指定赛季。
     */
    public AgentResponse chat(String message) {
        return chat(message, null);
    }

    /**
     * 同步问答：检索上下文 → 组装 prompt → 调 LLM → 返回结果。
     *
     * @param message   用户问题
     * @param seasonTag 赛季版本号（如 "s14"），可为 null，用于过滤知识库
     */
    public AgentResponse chat(String message, String seasonTag) {
        long start = System.currentTimeMillis();
        log.info("Agent chat: seasonTag={}", seasonTag);

        // 1. 从 ES 向量库检索相关文档
        String filter = seasonTag != null
                ? DocumentMetadata.VERSION_TAG + " == '" + seasonTag + "'"
                : null;
        RetrievalResult ctx = retrievalService.retrieve(message, filter);
        log.info("Pre-retrieved {} documents in {}ms", ctx.citations().size(), ctx.tookMs());

        // 2. 把检索到的引用信息拼入 system prompt
        String contextPrompt = buildContextPrompt(ctx.citations());

        // 3. 调用 LLM（ChatClient 自动处理 tool-calling）
        String answer = chatClient.prompt()
                .system(s -> s.text(contextPrompt))
                .user(message)
                .call()
                .content();

        long took = System.currentTimeMillis() - start;
        log.info("Agent responded in {}ms, citations={}", took, ctx.citations().size());

        return new AgentResponse(answer, ctx.citations());
    }

    /**
     * 带历史会话的问答。适用于多轮对话场景。
     *
     * @param history  Spring AI Message 列表（从 MemoryService.getHistory() 获取）
     */
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

        // 把历史消息一起发给 LLM，保持上下文连贯
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

    /**
     * SSE 流式问答。逐 token 返回文本，适合前端实时展示。
     * 返回 Flux<String>，ChatController 中映射为 text/event-stream。
     */
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

    /**
     * 把检索到的引用信息拼成 system prompt，告诉 LLM 有哪些事实依据可用。
     * 格式：[1] 标题 (来源: xxx) + 摘要内容
     * 要求 LLM 回答时标注引用编号。
     */
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
