package com.game.agent.memory.service;

import com.game.agent.memory.model.Citation;
import com.game.agent.memory.model.Message;
import com.game.agent.memory.model.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 会话与消息管理的高阶服务，是 GameAgent 和存储层之间的桥梁。
 * <p>
 * 职责：
 * - 创建/获取会话
 * - 记录用户和助手的消息
 * - 获取历史消息（转为 Spring AI Message 类型，供 ChatClient 使用）
 * - 触发蒸馏（消息超阈值时自动摘要历史）
 * <p>
 * 注：DistillService 通过 @Autowired(required=false) 注入，
 * 没有 ChatClient 时蒸馏自动跳过，不影响核心功能。
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final SessionService sessionService;
    private final MessageService messageService;
    private final DistillService distillService;
    private final int distillThreshold; // 每多少条消息触发一次蒸馏

    public MemoryService(
            SessionService sessionService,
            MessageService messageService,
            @Autowired(required = false) DistillService distillService,
            @Value("${game-agent.memory.distill-threshold:10}") int distillThreshold) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.distillService = distillService;
        this.distillThreshold = distillThreshold;
    }

    /** 创建新会话，返回 sessionId */
    public String createSession(String seasonTag) {
        Session session = sessionService.createSession(seasonTag);
        log.debug("Created session: {}", session.id());
        return session.id();
    }

    public Optional<Session> getSession(String sessionId) {
        return sessionService.getSession(sessionId);
    }

    public List<Session> listSessions(int page, int size) {
        return sessionService.listSessions(
                org.springframework.data.domain.PageRequest.of(page, size)).getContent();
    }

    /**
     * 记录用户消息。
     * 第一条用户消息会自动截取前 30 字作为会话标题。
     */
    public Message addUserMessage(String sessionId, String text) {
        int count = messageService.countBySession(sessionId);
        Message msg = messageService.addMessage(sessionId, "user", text, null);
        sessionService.touchSession(sessionId);

        // 首条消息 → 自动生成会话标题
        if (count == 0 && text.length() > 0) {
            String title = text.length() > 30 ? text.substring(0, 30) + "..." : text;
            sessionService.updateTitle(sessionId, title);
        }

        return msg;
    }

    /**
     * 记录助手消息。
     * 同时检查消息数是否达到蒸馏阈值，触发自动摘要。
     */
    public Message addAssistantMessage(String sessionId, String text, List<Citation> citations) {
        Message msg = messageService.addMessage(sessionId, "assistant", text, citations);
        sessionService.touchSession(sessionId);

        int count = messageService.countBySession(sessionId);
        if (distillService != null && count > 0 && count % distillThreshold == 0) {
            try {
                distillService.distill(sessionId);
            } catch (Exception e) {
                log.warn("Distill failed for session: {}", sessionId, e);
            }
        }

        return msg;
    }

    /**
     * 获取会话历史，转为 Spring AI Message 类型列表。
     * <p>
     * 转换规则：
     * - 已蒸馏的消息 → SystemMessage（摘要内容作为系统提示）
     * - user 角色 → UserMessage
     * - assistant 角色 → AssistantMessage
     * <p>
     * 这个列表可以直接传给 ChatClient.prompt().messages() 保持上下文。
     */
    public List<org.springframework.ai.chat.messages.Message> getHistory(String sessionId) {
        List<Message> messages = messageService.getSessionMessages(sessionId);
        List<org.springframework.ai.chat.messages.Message> result = new ArrayList<>();

        for (Message msg : messages) {
            if (msg.distilled() && msg.distillSummary() != null) {
                result.add(new SystemMessage(msg.distillSummary()));
            } else if ("user".equals(msg.role())) {
                result.add(new UserMessage(msg.text()));
            } else if ("assistant".equals(msg.role())) {
                result.add(new AssistantMessage(msg.text()));
            }
        }

        return result;
    }

    /** 删除会话及其所有消息 */
    public void deleteSession(String sessionId) {
        messageService.deleteSessionMessages(sessionId);
        sessionService.deleteSession(sessionId);
    }
}
