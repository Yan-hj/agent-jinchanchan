package com.game.agent.ingestion.config;

import com.game.agent.ingestion.service.SocialIngestionService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "game-agent.ingestion.social")
public class SocialSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(SocialSourceConfig.class);

    private final SocialIngestionService socialIngestionService;
    private List<Creator> creators = List.of();

    public SocialSourceConfig(SocialIngestionService socialIngestionService) {
        this.socialIngestionService = socialIngestionService;
    }

    public void setCreators(List<Creator> creators) {
        this.creators = creators;
    }

    @PostConstruct
    void registerCreators() {
        for (Creator c : creators) {
            socialIngestionService.registerCreator(c.getName(), c.getUid(), c.getSpaceUrl());
        }
        log.info("已从配置注册 {} 个B站UP主", creators.size());
    }

    public static class Creator {
        private String name;
        private String uid;
        private String spaceUrl;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUid() { return uid; }
        public void setUid(String uid) { this.uid = uid; }
        public String getSpaceUrl() { return spaceUrl; }
        public void setSpaceUrl(String spaceUrl) { this.spaceUrl = spaceUrl; }
    }
}
