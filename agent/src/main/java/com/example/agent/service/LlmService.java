package com.example.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 大模型服务
 * 直接调用通义千问 HTTP API
 */
@Slf4j
@Service
public class LlmService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${llm.api.model:qwen-max}")
    private String model;

    @Value("${llm.temperature:0.7}")
    private double temperature;

    public LlmService(WebClient dashScopeWebClient, ObjectMapper objectMapper) {
        this.webClient = dashScopeWebClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用大模型生成文本
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 生成的文本
     */
    public Mono<String> generate(String systemPrompt, String userPrompt) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);

        ObjectNode input = objectMapper.createObjectNode();
        ArrayNode messages = objectMapper.createArrayNode();

        // 系统消息
        ObjectNode systemMessage = objectMapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        messages.add(systemMessage);

        // 用户消息
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);
        messages.add(userMessage);

        input.set("messages", messages);
        requestBody.set("input", input);

        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("temperature", temperature);
        parameters.put("result_format", "message");
        requestBody.set("parameters", parameters);

        log.debug("LLM request: {}", requestBody);

        return webClient.post()
            .uri("/services/aigc/text-generation/generation")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(response -> {
                log.debug("LLM response: {}", response);
                return extractContent(response);
            })
            .onErrorResume(error -> {
                log.error("LLM call failed: {}", error.getMessage());
                return Mono.just("Error: " + error.getMessage());
            });
    }

    /**
     * 调用大模型生成文本（带历史消息）
     */
    public Mono<String> generateWithHistory(String systemPrompt, List<Message> history, String userPrompt) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);

        ObjectNode input = objectMapper.createObjectNode();
        ArrayNode messages = objectMapper.createArrayNode();

        // 系统消息
        ObjectNode systemMessage = objectMapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        messages.add(systemMessage);

        // 历史消息
        for (Message msg : history) {
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", msg.getRole());
            msgNode.put("content", msg.getContent());
            messages.add(msgNode);
        }

        // 用户消息
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);
        messages.add(userMessage);

        input.set("messages", messages);
        requestBody.set("input", input);

        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("temperature", temperature);
        parameters.put("result_format", "message");
        requestBody.set("parameters", parameters);

        return webClient.post()
            .uri("/services/aigc/text-generation/generation")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(this::extractContent)
            .onErrorResume(error -> {
                log.error("LLM call failed: {}", error.getMessage());
                return Mono.just("Error: " + error.getMessage());
            });
    }

    /**
     * 从响应中提取内容
     */
    private String extractContent(JsonNode response) {
        try {
            if (response.has("output")) {
                JsonNode output = response.get("output");
                if (output.has("choices") && output.get("choices").isArray() && output.get("choices").size() > 0) {
                    JsonNode choice = output.get("choices").get(0);
                    if (choice.has("message") && choice.get("message").has("content")) {
                        return choice.get("message").get("content").asText();
                    }
                }
            }
            return response.toString();
        } catch (Exception e) {
            log.error("Failed to extract content: {}", e.getMessage());
            return response.toString();
        }
    }

    /**
     * 消息类
     */
    public static class Message {
        private final String role;
        private final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }
}
