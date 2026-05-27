package com.game.agent.core.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SkillDocTool {

    private static final Logger log = LoggerFactory.getLogger(SkillDocTool.class);

    private static final String SKILL_DIR = "skills";

    @Tool(name = "loadSkillDoc", description = "读取本地策略规则文档（最高优先级），支持羁绊、经济运营、站位、装备合成等规则")
    public String loadSkillDoc(
            @ToolParam(required = true, description = "文档名: synergies(羁绊), economy(经济运营), positioning(站位), items(装备合成)") String skillName) {
        log.info("Tool loadSkillDoc: skillName={}", skillName);

        String path = SKILL_DIR + "/" + skillName + ".md";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                return "策略文档 '" + skillName + "' 不存在，支持的文档: synergies, economy, positioning, items";
            }
            byte[] bytes = resource.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read skill doc: {}", path, e);
            return "读取策略文档失败: " + e.getMessage();
        }
    }
}
