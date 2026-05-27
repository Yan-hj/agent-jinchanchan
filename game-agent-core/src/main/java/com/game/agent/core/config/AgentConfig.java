package com.game.agent.core.config;

import com.game.agent.core.tool.OfficialSearchTool;
import com.game.agent.core.tool.SkillDocTool;
import com.game.agent.core.tool.SocialSearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    private static final String SYSTEM_PROMPT = """
            你是《金铲铲之战》（Teamfight Tactics Mobile）游戏策略助手，提供阵容推荐、装备合成、经济运营、站位调整等专业策略建议。

            ## 核心原则
            1. 基于检索到的知识回答，不要编造或猜测具体数值
            2. 回答中引用来源时使用角标标记，如「根据官方公告[1]」
            3. 给出具体、可执行的操作建议，避免空泛描述
            4. 对于版本更新类问题，优先关注最新版本的变化
            5. 如果检索到的信息不足以回答，使用工具进行定向搜索

            ## 回答风格
            - 简洁直接，避免冗余
            - 关键数据和名称要准确（英雄名、装备名、羁绊名）
            - 适当使用分段和要点
            - 不确定的信息要明确说明
            """;

    @Bean
    public ChatClient gameAgentChatClient(ChatClient.Builder builder,
                                          OfficialSearchTool officialSearchTool,
                                          SocialSearchTool socialSearchTool,
                                          SkillDocTool skillDocTool) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(officialSearchTool, socialSearchTool, skillDocTool)
                .build();
    }
}
