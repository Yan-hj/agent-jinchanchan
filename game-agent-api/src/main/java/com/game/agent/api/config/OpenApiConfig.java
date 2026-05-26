package com.game.agent.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gameAgentOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Game Strategy Agent API")
                        .description("金铲铲之战游戏策略 Agent REST API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Game Agent Team")));
    }
}
