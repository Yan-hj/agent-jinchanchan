package com.game.agent.ingestion.collector;

import com.game.agent.common.metadata.SourceType;

import java.util.List;

public interface ContentSource {
    SourceType sourceType();
    List<RawContent> fetch();
    boolean supportsUrl(String url);
}
