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

import java.util.Map;

/**
 * MCP 工具客户端
 * 用于调用 MCP Server 提供的工具
 */
@Slf4j
@Component
public class McpToolClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${mcp.server.url}")
    private String mcpServerUrl;

    @Value("${mcp.server.tools.applyResource}")
    private String applyResourcePath;

    @Value("${mcp.server.tools.recycleResource}")
    private String recycleResourcePath;

    @Value("${mcp.server.tools.autoInterfaceTest}")
    private String autoInterfaceTestPath;

    @Value("${mcp.server.tools.autoUITest}")
    private String autoUITestPath;

    @Value("${mcp.server.tools.resultAnalysis}")
    private String resultAnalysisPath;

    @Value("${mcp.server.tools.readMemory}")
    private String readMemoryPath;

    @Value("${mcp.server.tools.writeMemory}")
    private String writeMemoryPath;

    public McpToolClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * 申请环境资源
     */
    public Mono<JsonNode> applyResource(Map<String, Object> params) {
        return callTool(applyResourcePath, params);
    }

    /**
     * 回收环境资源
     */
    public Mono<JsonNode> recycleResource(Map<String, Object> params) {
        return callTool(recycleResourcePath, params);
    }

    /**
     * 自动化接口测试
     */
    public Mono<JsonNode> autoInterfaceTest(Map<String, Object> params) {
        return callTool(autoInterfaceTestPath, params);
    }

    /**
     * 自动化界面测试
     */
    public Mono<JsonNode> autoUITest(Map<String, Object> params) {
        return callTool(autoUITestPath, params);
    }

    /**
     * 测试结果分析
     */
    public Mono<JsonNode> resultAnalysis(Map<String, Object> params) {
        return callTool(resultAnalysisPath, params);
    }

    /**
     * 读取记忆
     */
    public Mono<JsonNode> readMemory(Map<String, Object> params) {
        return callTool(readMemoryPath, params);
    }

    /**
     * 写入记忆（异步执行）
     */
    public void writeMemoryAsync(Map<String, Object> params) {
        callTool(writeMemoryPath, params)
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
        return callTool(writeMemoryPath, params);
    }

    /**
     * 调用工具的统一方法
     */
    private Mono<JsonNode> callTool(String toolPath, Map<String, Object> params) {
        String url = mcpServerUrl + toolPath;
        log.info("Calling MCP tool: {}, params: {}", url, params);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.set("params", objectMapper.valueToTree(params));

        return webClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .doOnSuccess(response -> log.info("MCP tool response: {}", response))
            .doOnError(error -> log.error("MCP tool error: {}", error.getMessage()));
    }
}
