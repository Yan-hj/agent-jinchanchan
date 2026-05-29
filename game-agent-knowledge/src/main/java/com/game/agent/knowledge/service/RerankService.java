package com.game.agent.knowledge.service;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * DashScope Rerank 模型封装服务。
 * <p>
 * Rerank 的作用：向量检索召回了 topK×3 候选，但向量相似度不一定等于语义相关性。
 * Rerank 模型会逐条评估 query 和文档的相关性，给出更精确的排序分数。
 * <p>
 * 关键设计：
 * - @ConditionalOnBean(RerankModel.class) — 没有 DashScope API Key 时不加载，不阻塞启动
 * - 降级机制：Rerank 调用抛异常时，fallback 到取向量搜索的前 topK 条
 * - rerank_min_score：低于此分数的结果被过滤，避免无关内容混入
 */
@Service
@ConditionalOnBean(RerankModel.class)
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    private final RerankModel rerankModel;
    private final double rerankMinScore;

    public RerankService(
            RerankModel rerankModel,
            @Value("${game-agent.knowledge.rerank.min-score:0.3}") double rerankMinScore) {
        this.rerankModel = rerankModel;
        this.rerankMinScore = rerankMinScore;
    }

    /**
     * 对候选文档执行精排。
     *
     * @param query      用户原始 query，rerank 模型用它来评估相关性
     * @param candidates 向量检索召回的候选文档列表
     * @param topK       最终保留的结果数
     * @return 精排后的文档列表（已按相关性降序）
     */
    public List<Document> rerank(String query, List<Document> candidates, int topK) {
        if (candidates.isEmpty()) {
            return candidates;
        }

        try {
            // DashScope Rerank 通过 options 指定返回的 topN
            var options = DashScopeRerankOptions.builder()
                    .withTopN(Math.min(topK, candidates.size()))
                    .build();

            var request = new RerankRequest(query, candidates, options);
            var response = rerankModel.call(request);

            // 过滤低分结果，并把 rerank 分数写入文档 metadata 供后续使用
            return response.getResults().stream()
                    .filter(r -> r.getScore() != null && r.getScore() >= rerankMinScore)
                    .map(r -> {
                        Document doc = r.getOutput();
                        doc.getMetadata().put("rerank_score", r.getScore());
                        return doc;
                    })
                    .toList();
        } catch (Exception e) {
            // 降级：Rerank 失败（如网络超时、API 限流），用向量搜索的原始排序
            log.warn("Rerank call failed, falling back to topK from vector search", e);
            return candidates.size() > topK ? candidates.subList(0, topK) : candidates;
        }
    }
}
