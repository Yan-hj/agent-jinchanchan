package com.game.agent.memory.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "sessions")
public record Session(
        @Id String id,
        String title,
        @Field("message_count") int messageCount,
        @Field("season_tag") String seasonTag,
        @Field("last_message_at") LocalDateTime lastMessageAt,
        @Field("created_at") LocalDateTime createdAt,
        @Field("updated_at") LocalDateTime updatedAt
) {
    public Session withTitle(String title) {
        return new Session(id, title, messageCount, seasonTag, lastMessageAt, createdAt, updatedAt);
    }

    public Session withMessageCount(int messageCount) {
        return new Session(id, title, messageCount, seasonTag, lastMessageAt, createdAt, updatedAt);
    }

    public Session withLastMessageAt(LocalDateTime lastMessageAt) {
        return new Session(id, title, messageCount, seasonTag, lastMessageAt, createdAt, updatedAt);
    }

    public Session withUpdatedAt(LocalDateTime updatedAt) {
        return new Session(id, title, messageCount, seasonTag, lastMessageAt, createdAt, updatedAt);
    }
}
