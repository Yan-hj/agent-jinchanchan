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

    public List<Document> rerank(String query, List<Document> candidates, int topK) {
        if (candidates.isEmpty()) {
            return candidates;
        }

        try {
            var options = DashScopeRerankOptions.builder()
                    .withTopN(Math.min(topK, candidates.size()))
                    .build();

            var request = new RerankRequest(query, candidates, options);
            var response = rerankModel.call(request);

            return response.getResults().stream()
                    .filter(r -> r.getScore() != null && r.getScore() >= rerankMinScore)
                    .map(r -> {
                        Document doc = r.getOutput();
                        doc.getMetadata().put("rerank_score", r.getScore());
                        return doc;
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("Rerank call failed, falling back to topK from vector search", e);
            return candidates.size() > topK ? candidates.subList(0, topK) : candidates;
        }
    }
}
