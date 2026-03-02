package com.example.devtestagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import java.util.Map;

/**
 * MCP 工具客户端
 * 使用 MCP 协议调用工具
 */
@Slf4j
@Component
public class McpToolClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${mcp.server.url:http://localhost:3000}")
    private String mcpServerUrl;

    public McpToolClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        log.info("MCP Client initialized, server URL: {}", mcpServerUrl);
    }

    /**
     * 调用 MCP 工具
     */
    private Mono<JsonNode> callTool(String toolName, Map<String, Object> arguments) {
        String url = mcpServerUrl + "/mcp/tools/call";
        log.info("Calling MCP tool: {}, arguments: {}", toolName, arguments);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("name", toolName);
        requestBody.set("arguments", objectMapper.valueToTree(arguments));

        return webClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .doOnSuccess(response -> log.info("MCP tool response: {}", response))
            .doOnError(error -> log.error("MCP tool error: {}", error.getMessage()));
    }

    // ==================== 环境资源管理工具 ====================

    /**
     * 申请环境资源
     */
    public Mono<JsonNode> applyResource(Map<String, Object> params) {
        return callTool("applyResource", params);
    }

    /**
     * 回收环境资源
     */
    public Mono<JsonNode> recycleResource(Map<String, Object> params) {
        return callTool("recycleResource", params);
    }

    // ==================== 传统测试工具 ====================

    /**
     * 自动化接口测试
     */
    public Mono<JsonNode> autoInterfaceTest(Map<String, Object> params) {
        return callTool("autoInterfaceTest", params);
    }

    /**
     * 自动化界面测试
     */
    public Mono<JsonNode> autoUITest(Map<String, Object> params) {
        return callTool("autoUITest", params);
    }

    /**
     * 测试结果分析
     */
    public Mono<JsonNode> resultAnalysis(Map<String, Object> params) {
        return callTool("resultAnalysis", params);
    }

    // ==================== 测试批次管理工具 ====================

    /**
     * 创建测试批次
     */
    public Mono<JsonNode> createBatch(Map<String, Object> params) {
        return callTool("createBatch", params);
    }

    /**
     * 添加案例到批次
     */
    public Mono<JsonNode> addCasesToBatch(Map<String, Object> params) {
        return callTool("addCasesToBatch", params);
    }

    /**
     * 执行批次
     */
    public Mono<JsonNode> executeBatch(Map<String, Object> params) {
        return callTool("executeBatch", params);
    }

    /**
     * 批次结果分析
     */
    public Mono<JsonNode> analyzeBatchResult(Map<String, Object> params) {
        return callTool("analyzeBatchResult", params);
    }

    // ==================== 记忆工具 ====================

    /**
     * 读取记忆
     */
    public Mono<JsonNode> readMemory(Map<String, Object> params) {
        return callTool("readMemory", params);
    }

    /**
     * 写入记忆（异步执行）
     */
    public void writeMemoryAsync(Map<String, Object> params) {
        callTool("writeMemory", params)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                result -> log.info("Memory write success: {}", result),
                error -> log.error("Memory write failed: {}", error.getMessage())
            );
    }

    /**
     * 同步写入记忆
     */
    public Mono<JsonNode> writeMemory(Map<String, Object> params) {
        return callTool("writeMemory", params);
    }
}
