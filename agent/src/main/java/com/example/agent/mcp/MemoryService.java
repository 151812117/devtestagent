package com.example.agent.mcp;

import com.example.agent.model.MemoryContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 记忆服务
 * 封装记忆相关的操作
 */
@Slf4j
@Service
public class MemoryService {

    private final McpToolClient mcpToolClient;
    private final ObjectMapper objectMapper;

    public MemoryService(McpToolClient mcpToolClient, ObjectMapper objectMapper) {
        this.mcpToolClient = mcpToolClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 读取用户记忆
     */
    public Mono<MemoryContext> readMemory(String userId, String sessionId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("sessionId", sessionId);

        return mcpToolClient.readMemory(params)
            .map(response -> parseMemoryContext(response, userId, sessionId))
            .onErrorResume(error -> {
                log.error("Failed to read memory: {}", error.getMessage());
                return Mono.just(createEmptyMemoryContext(userId, sessionId));
            });
    }

    /**
     * 异步写入记忆
     */
    public void writeMemoryAsync(String userId, String sessionId, String action, 
                                  String target, String result, String details) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("sessionId", sessionId);
        params.put("action", action);
        params.put("target", target);
        params.put("result", result);
        params.put("details", details);
        params.put("timestamp", LocalDateTime.now().toString());

        mcpToolClient.writeMemoryAsync(params);
    }

    /**
     * 同步写入记忆
     */
    public Mono<Boolean> writeMemory(String userId, String sessionId, String action,
                                      String target, String result, String details) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("sessionId", sessionId);
        params.put("action", action);
        params.put("target", target);
        params.put("result", result);
        params.put("details", details);
        params.put("timestamp", LocalDateTime.now().toString());

        return mcpToolClient.writeMemory(params)
            .map(response -> true)
            .onErrorResume(error -> {
                log.error("Failed to write memory: {}", error.getMessage());
                return Mono.just(false);
            });
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
        writeMemoryAsync(userId, sessionId, testType, "TEST_" + scenario, result,
            "Test operation: " + testType + " for scenario: " + scenario);
    }

    /**
     * 解析记忆上下文
     */
    private MemoryContext parseMemoryContext(JsonNode response, String userId, String sessionId) {
        try {
            if (response.has("data")) {
                return objectMapper.treeToValue(response.get("data"), MemoryContext.class);
            }
            return createEmptyMemoryContext(userId, sessionId);
        } catch (Exception e) {
            log.error("Failed to parse memory context: {}", e.getMessage());
            return createEmptyMemoryContext(userId, sessionId);
        }
    }

    /**
     * 创建空记忆上下文
     */
    private MemoryContext createEmptyMemoryContext(String userId, String sessionId) {
        return MemoryContext.builder()
            .userId(userId)
            .sessionId(sessionId)
            .build();
    }
}
