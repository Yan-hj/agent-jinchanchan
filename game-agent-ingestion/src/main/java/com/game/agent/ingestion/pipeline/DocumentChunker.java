package com.game.agent.ingestion.pipeline;

import com.game.agent.ingestion.collector.RawContent;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DocumentChunker {

    private final int chunkSize;
    private final int chunkOverlap;

    public DocumentChunker(
            @Value("${game-agent.ingestion.chunk.size:512}") int chunkSize,
            @Value("${game-agent.ingestion.chunk.overlap:64}") int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public List<Document> chunk(RawContent raw, Map<String, Object> metadata) {
        String body = raw.body();
        if (body == null || body.isBlank()) {
            return List.of();
        }

        List<Document> chunks = new ArrayList<>();
        int start = 0;
        int seq = 0;

        while (start < body.length()) {
            int end = Math.min(start + chunkSize, body.length());
            String text = body.substring(start, end).trim();
            if (!text.isEmpty()) {
                Map<String, Object> chunkMeta = new java.util.HashMap<>(metadata);
                chunkMeta.put("chunk_seq", seq);
                chunkMeta.put("chunk_total", -1);
                chunks.add(new Document(text, chunkMeta));
                seq++;
            }
            start += (chunkSize - chunkOverlap);
        }

        int total = seq;
        for (Document doc : chunks) {
            doc.getMetadata().put("chunk_total", total);
        }
        return chunks;
    }
}
