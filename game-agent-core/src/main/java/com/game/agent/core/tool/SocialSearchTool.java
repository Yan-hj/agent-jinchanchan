package com.game.agent.core.tool;

import com.game.agent.common.metadata.DocumentMetadata;
import com.game.agent.common.metadata.SourceType;
import com.game.agent.knowledge.service.KnowledgeRetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

@Component
public class SocialSearchTool {

    private static final Logger log = LoggerFactory.getLogger(SocialSearchTool.class);

    private final KnowledgeRetrievalService retrievalService;

    public SocialSearchTool(KnowledgeRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Tool(name = "searchSocialStrategy", description = "搜索社媒策略内容（博主阵容推荐、站位思路、运营节奏等）")
    public String searchSocialStrategy(
            @ToolParam(required = true, description = "检索关键词，如 'S14 法师 阵容'") String query,
            @ToolParam(required = false, description = "最低可信度阈值 0.0-1.0，默认 0.3") Double minConfidence) {
        log.info("Tool searchSocialStrategy: query={}, minConfidence={}", query, minConfidence);

        String filter = DocumentMetadata.SOURCE_TYPE + " == '" + SourceType.SOCIAL.value() + "'";
        var result = retrievalService.retrieve(query, filter);

        List<Document> docs = result.documents();
        if (minConfidence != null && minConfidence > 0) {
            docs = docs.stream()
                    .filter(d -> {
                        Object score = d.getMetadata().get(DocumentMetadata.CONFIDENCE_SCORE);
                        return score instanceof Number n && n.doubleValue() >= minConfidence;
                    })
                    .toList();
        }
        return formatDocuments(docs);
    }

    private String formatDocuments(List<Document> docs) {
        if (docs.isEmpty()) {
            return "未找到相关的社媒策略内容。";
        }
        StringJoiner sj = new StringJoiner("\n---\n");
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            sj.add("[" + (i + 1) + "]" + doc.getMetadata().getOrDefault("title", "Untitled")
                    + "\n作者: " + doc.getMetadata().getOrDefault("author", "unknown")
                    + "\n可信度: " + doc.getMetadata().getOrDefault(DocumentMetadata.CONFIDENCE_SCORE, "N/A")
                    + "\n" + doc.getText());
        }
        return sj.toString();
    }
}
