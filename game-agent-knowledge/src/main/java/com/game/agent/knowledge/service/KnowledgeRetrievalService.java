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

/**
 * 混合检索服务（向量召回 + Rerank 精排）。
 * <p>
 * 是 Agent 知识库的核心入口，调用链路：
 * VectorStore.similaritySearch (topK × 3 候选) → RerankService.rerank (精排到 topK) → 构建 Citations
 * <p>
 * 支持按 source_type（official/social/skill）和 version_tag 的过滤。
 * 无 ES 时不会注册此 Bean（通过 @ConditionalOnBean 控制），GameAgent 自动跳过检索。
 */
@Service
public class KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    private final VectorStore vectorStore;
    private final RerankService rerankService;
    private final int topK;                    // 最终返回的结果数
    private final double similarityThreshold;   // 向量相似度阈值，低于此值的结果被过滤
    private final int candidateMultiplier;      // 候选数 = topK × candidateMultiplier，给 rerank 更多候选

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

    /**
     * 不带过滤检索（查全部知识库）。
     */
    public RetrievalResult retrieve(String query) {
        return retrieve(query, topK, (String) null);
    }

    /**
     * 带过滤表达式检索。过滤语法：如 "source_type == 'official' && version_tag == 's14'"
     */
    public RetrievalResult retrieve(String query, String filter) {
        return retrieve(query, topK, filter);
    }

    /**
     * 完整检索方法。
     * <p>
     * 1. 向量检索：从 ES 召回 topK × candidateMultiplier 个候选文档
     * 2. Rerank：DashScope Rerank 模型对候选结果精排
     * 3. 构建 Citations：从 Document metadata 提取来源信息
     */
    public RetrievalResult retrieve(String query, int topK, String filter) {
        long start = System.currentTimeMillis();

        // 召回更多候选给 rerank 排序用
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

        // DashScope Rerank 精排（降级：如果 rerank 失败则用向量搜索的原始 topK）
        List<Document> ranked = rerankService.rerank(query, results, topK);

        List<Citation> citations = ranked.stream()
                .map(this::toCitation)
                .toList();

        long took = System.currentTimeMillis() - start;
        log.info("Retrieval completed in {}ms: {} documents, {} citations", took, ranked.size(), citations.size());

        return new RetrievalResult(ranked, citations, took);
    }

    /**
     * 把 Spring AI Document 转成项目统一的 Citation 记录。
     * metadata 中的字段由 ingestion 阶段写入。
     */
    private Citation toCitation(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        String text = doc.getText();

        return new Citation(
                str(meta, DocumentMetadata.SOURCE, "unknown"),
                str(meta, DocumentMetadata.SOURCE_TYPE, "official"),
                str(meta, "title", "Untitled"),
                str(meta, DocumentMetadata.CITATION_URL, null),
                // 摘要截取前 200 字符，避免太长
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
