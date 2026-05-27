package com.game.agent.knowledge.model;

import org.springframework.ai.document.Document;

import java.util.List;

public record RetrievalResult(
        List<Document> documents,
        List<Citation> citations,
        long tookMs
) {}
