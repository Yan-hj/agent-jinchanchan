package com.game.agent.knowledge.service;

import com.game.agent.common.metadata.DocumentMetadata;
import com.game.agent.knowledge.model.Citation;
import com.game.agent.knowledge.model.RetrievalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    private final VectorStore vectorStore;
    private final RerankService rerankService;
    private final int topK;
    private final double similarityThreshold;
    private final int candidateMultiplier;

    public KnowledgeRetrievalService(
            VectorStore vectorStore,
            RerankService rerankService,
            @Value("${game-agent.knowledge.retrieval.top-k:5}") int topK,
            @Value("${game-agent.knowledge.retrieval.similarity-threshold:0.6}") double similarityThreshold,
            @Value("${game-agent.knowledge.retrieval.candidate-multiplier:3}") int candidateMultiplier) {
        this.vectorStore = vectorStore;
        this.rerankService = rerankService;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.candidateMultiplier = candidateMultiplier;
    }

    public RetrievalResult retrieve(String query) {
        return retrieve(query, topK, (String) null);
    }

    public RetrievalResult retrieve(String query, String filter) {
        return retrieve(query, topK, filter);
    }

    public RetrievalResult retrieve(String query, int topK, String filter) {
        long start = System.currentTimeMillis();

        int candidates = topK * candidateMultiplier;
        var builder = SearchRequest.builder()
                .query(query)
                .similarityThreshold(similarityThreshold)
                .topK(candidates);
        if (filter != null) {
            builder = builder.filterExpression(filter);
        }

        List<Document> results = vectorStore.similaritySearch(builder.build());
        log.debug("Vector search returned {} candidates for query: {}", results.size(), query);

        List<Document> ranked = rerankService.rerank(query, results, topK);

        List<Citation> citations = ranked.stream()
                .map(this::toCitation)
                .toList();

        long took = System.currentTimeMillis() - start;
        log.info("Retrieval completed in {}ms: {} documents, {} citations", took, ranked.size(), citations.size());

        return new RetrievalResult(ranked, citations, took);
    }

    private Citation toCitation(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        String text = doc.getText();

        return new Citation(
                str(meta, DocumentMetadata.SOURCE, "unknown"),
                str(meta, DocumentMetadata.SOURCE_TYPE, "official"),
                str(meta, "title", "Untitled"),
                str(meta, DocumentMetadata.CITATION_URL, null),
                text != null && text.length() > 200 ? text.substring(0, 200) + "..." : text,
                dbl(meta, DocumentMetadata.CONFIDENCE_SCORE, 1.0)
        );
    }

    private String str(Map<String, Object> meta, String key, String fallback) {
        Object v = meta.get(key);
        return v != null ? v.toString() : fallback;
    }

    private double dbl(Map<String, Object> meta, String key, double fallback) {
        Object v = meta.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return fallback;
    }
}
