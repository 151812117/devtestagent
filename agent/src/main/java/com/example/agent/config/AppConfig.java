package com.example.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 应用配置类
 */
@Configuration
public class AppConfig {

    @Value("classpath:/prompts/master-agent-system-prompt.txt")
    private Resource masterAgentSystemPrompt;

    @Value("classpath:/prompts/env-agent-system-prompt.txt")
    private Resource envAgentSystemPrompt;

    @Value("classpath:/prompts/test-agent-system-prompt.txt")
    private Resource testAgentSystemPrompt;

    @Value("classpath:/prompts/menu-recommendation-system-prompt.txt")
    private Resource menuRecommendationSystemPrompt;

    @Value("${llm.api.key:}")
    private String apiKey;

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebClient dashScopeWebClient(WebClient.Builder builder) {
        return builder
            .baseUrl("https://dashscope.aliyuncs.com/api/v1")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean(name = "masterAgentSystemPrompt")
    public Resource masterAgentSystemPrompt() {
        return masterAgentSystemPrompt;
    }

    @Bean(name = "envAgentSystemPrompt")
    public Resource envAgentSystemPrompt() {
        return envAgentSystemPrompt;
    }

    @Bean(name = "testAgentSystemPrompt")
    public Resource testAgentSystemPrompt() {
        return testAgentSystemPrompt;
    }

    @Bean(name = "menuRecommendationSystemPrompt")
    public Resource menuRecommendationSystemPrompt() {
        return menuRecommendationSystemPrompt;
    }
}
