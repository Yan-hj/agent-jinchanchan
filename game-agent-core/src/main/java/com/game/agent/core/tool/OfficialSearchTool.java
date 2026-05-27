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
public class OfficialSearchTool {

    private static final Logger log = LoggerFactory.getLogger(OfficialSearchTool.class);

    private final KnowledgeRetrievalService retrievalService;

    public OfficialSearchTool(KnowledgeRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @Tool(name = "searchOfficialMeta", description = "搜索官方元数据（版本公告、平衡性调整、英雄/羁绊改动等）")
    public String searchOfficialMeta(
            @ToolParam(required = true, description = "赛季版本号，如 's14'") String versionTag,
            @ToolParam(required = true, description = "用户问题关键词，如 '法师 加强'") String query) {
        log.info("Tool searchOfficialMeta: versionTag={}, query={}", versionTag, query);

        String filter = DocumentMetadata.SOURCE_TYPE + " == '" + SourceType.OFFICIAL.value() + "'"
                + " && " + DocumentMetadata.VERSION_TAG + " == '" + versionTag + "'";

        var result = retrievalService.retrieve(query, filter);
        return formatDocuments(result.documents());
    }

    private String formatDocuments(List<Document> docs) {
        if (docs.isEmpty()) {
            return "未找到相关官方信息。";
        }
        StringJoiner sj = new StringJoiner("\n---\n");
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            sj.add("[" + (i + 1) + "] " + doc.getMetadata().getOrDefault("title", "Untitled")
                    + "\n来源: " + doc.getMetadata().getOrDefault("source", "unknown")
                    + "\n" + doc.getText());
        }
        return sj.toString();
    }
}
