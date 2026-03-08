package com.example.gateway.client;

import com.example.gateway.model.MemoryContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 记忆服务客户端
 * 调用 MCP Server 的 readMemory 工具
 */
@Slf4j
@Component
public class MemoryClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${mcp.server.url:http://localhost:3000}")
    private String mcpServerUrl;

    public MemoryClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * 读取用户记忆
     */
    public Mono<MemoryContext> readMemory(String userId, String sessionId) {
        log.info("[MemoryClient] Reading memory for userId: {}, sessionId: {}", userId, sessionId);

        Map<String, Object> request = Map.of(
            "name", "readMemory",
            "arguments", Map.of(
                "userId", userId != null ? userId : "anonymous",
                "sessionId", sessionId != null ? sessionId : "default",
                "limit", 10,
                "includeLongTerm", true
            )
        );

        return webClient.post()
            .uri(mcpServerUrl + "/mcp/tools/call")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(this::extractMemoryContext)
            .doOnNext(context -> log.info("[MemoryClient] Memory read success: {}", context))
            .onErrorResume(error -> {
                log.error("[MemoryClient] Failed to read memory: {}", error.getMessage());
                return Mono.just(MemoryContext.builder().build());
            });
    }

    private MemoryContext extractMemoryContext(JsonNode response) {
        try {
            if (response.has("data")) {
                JsonNode data = response.get("data");
                return objectMapper.convertValue(data, MemoryContext.class);
            }
            return MemoryContext.builder().build();
        } catch (Exception e) {
            log.error("Failed to parse memory context: {}", e.getMessage());
            return MemoryContext.builder().build();
        }
    }

    /**
     * 写入记忆（异步）
     */
    public void writeMemoryAsync(String userId, String sessionId, String action, 
                                  String target, String result, String details) {
        log.info("[MemoryClient] Writing memory async for userId: {}, action: {}", userId, action);

        Map<String, Object> request = Map.of(
            "name", "writeMemory",
            "arguments", Map.of(
                "userId", userId != null ? userId : "anonymous",
                "sessionId", sessionId != null ? sessionId : "default",
                "action", action,
                "target", target,
                "result", result,
                "details", details,
                "timestamp", LocalDateTime.now().toString()
            )
        );

        webClient.post()
            .uri(mcpServerUrl + "/mcp/tools/call")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .doOnNext(response -> log.info("[MemoryClient] Memory write success: request={}, response={}",request, response))
            .onErrorResume(error -> {
                log.error("[MemoryClient] Failed to write memory: {}", error.getMessage());
                return Mono.empty();
            })
            .subscribe();
    }

    /**
     * 记录环境操作
     */
    public void recordEnvOperation(String userId, String sessionId, String operation,
                                    String envType, String result) {
        writeMemoryAsync(userId, sessionId, operation, "ENV_" + envType, result, 
            "Environment operation: " + operation + " for type: " + envType);
    }

    /**
     * 记录测试操作
     */
    public void recordTestOperation(String userId, String sessionId, String testType,
                                     String scenario, String result) {
        writeMemoryAsync(userId, sessionId, testType, scenario, result,
            "Test operation: " + testType + " for scenario: " + scenario);
    }
}
