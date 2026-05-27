package com.game.agent.memory.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "messages")
public record Message(
        @Id String id,
        @Field("session_id") String sessionId,
        String role,
        String text,
        List<Citation> citations,
        boolean distilled,
        @Field("distill_summary") String distillSummary,
        @Field("created_at") LocalDateTime createdAt
) {
    public Message withId(String id) {
        return new Message(id, sessionId, role, text, citations, distilled, distillSummary, createdAt);
    }

    public Message withDistilled(boolean distilled) {
        return new Message(id, sessionId, role, text, citations, distilled, distillSummary, createdAt);
    }

    public Message withDistillSummary(String distillSummary) {
        return new Message(id, sessionId, role, text, citations, distilled, distillSummary, createdAt);
    }
}
