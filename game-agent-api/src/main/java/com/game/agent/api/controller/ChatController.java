package com.game.agent.api.controller;

import com.game.agent.api.model.*;
import com.game.agent.api.service.ChatService;
import com.game.agent.common.ApiResponse;
import com.game.agent.common.PagedResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对话 API 控制器。
 * <p>
 * 提供同步和流式两种对话方式，以及会话列表/历史查询。
 * <p>
 * 端点总览：
 * POST   /api/v1/chat                      — 同步问答
 * GET    /api/v1/chat/stream?message=xxx   — SSE 流式问答
 * GET    /api/v1/chat/sessions             — 会话列表
 * GET    /api/v1/chat/sessions/{id}        — 会话详情
 * GET    /api/v1/chat/sessions/{id}/messages — 消息历史
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 同步问答。内部流程：
     * 保存用户消息 → 检索知识库 → 调 LLM → 保存回答 → 返回 { content, citations }
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Chat request: sessionId={}, message={}", request.sessionId(), request.message());
        ChatResponse response = chatService.chat(
                request.message(), request.sessionId(), request.seasonTag());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * SSE 流式问答。event-stream 格式：
     * <pre>
     * event: message
     * data: {"text": "当前版本 T0 阵容是"}
     *
     * event: message
     * data: {"text": " 命运法师"}
     *
     * ... 客户端持续读取直到 connection 关闭
     * </pre>
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public reactor.core.publisher.Flux<String> chatStream(
            @RequestParam("message") String message,
            @RequestParam(name = "session_id", required = false) String sessionId,
            @RequestParam(name = "season_tag", required = false) String seasonTag) {
        log.info("Chat stream request: sessionId={}", sessionId);
        return chatService.chatStream(message, sessionId, seasonTag)
                .map(text -> "event: message\ndata: " + text + "\n\n");
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<PagedResponse<SessionSummary>>> listSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<SessionSummary> sessions = chatService.listSessions(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.of(sessions, page, size, sessions.size())));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<SessionSummary>> getSession(@PathVariable String sessionId) {
        return chatService.getSession(sessionId)
                .map(s -> ResponseEntity.ok(ApiResponse.success(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<List<MessageHistory>>> getMessages(
            @PathVariable String sessionId) {
        List<MessageHistory> messages = chatService.getMessages(sessionId);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }
}
