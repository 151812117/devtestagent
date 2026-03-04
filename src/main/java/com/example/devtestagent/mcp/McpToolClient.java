package com.example.devtestagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具客户端
 * 使用 MCP SSE 协议调用工具
 */
@Slf4j
@Component
public class McpToolClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${mcp.server.url:http://localhost:3000}")
    private String mcpServerUrl;
    
    // 缓存工具列表
    private final Map<String, JsonNode> toolCache = new ConcurrentHashMap<>();
    // 缓存 endpoint（从 SSE 获取）
    private volatile String mcpEndpoint = null;
    private volatile boolean initialized = false;

    public McpToolClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        log.info("MCP Client initialized, server URL: {}", mcpServerUrl);
        // 初始化时获取 SSE endpoint
        initializeSseEndpoint();
    }
    
    /**
     * 初始化 SSE 连接并获取 endpoint
     */
    private void initializeSseEndpoint() {
        try {
            // 尝试获取工具列表（直接 HTTP POST）
            fetchToolsList();
        } catch (Exception e) {
            log.error("Failed to initialize MCP connection: {}", e.getMessage());
        }
    }
    
    /**
     * 通过 SSE 获取 endpoint
     */
    private void fetchSseEndpoint() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(mcpServerUrl + "/mcp");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.debug("SSE received: {}", line);
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if (data.startsWith("http")) {
                                mcpEndpoint = data;
                                log.info("MCP endpoint discovered: {}", mcpEndpoint);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch SSE endpoint: {}", e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    /**
     * 获取 MCP Server 工具列表
     */
    private void fetchToolsList() {
        try {
            String url = mcpServerUrl + "/mcp/tools/list";
            log.info("Fetching MCP tools list from: {}", url);
            
            JsonNode response = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
            
            if (response != null && response.has("tools")) {
                for (JsonNode tool : response.get("tools")) {
                    String toolName = tool.get("name").asText();
                    toolCache.put(toolName, tool);
                    log.debug("Cached tool: {}", toolName);
                }
                initialized = true;
                log.info("MCP tools loaded: {}", toolCache.keySet());
            }
        } catch (Exception e) {
            log.error("Failed to fetch MCP tools list: {}", e.getMessage());
        }
    }

    /**
     * 调用 MCP 工具
     */
    public Mono<JsonNode> callTool(String toolName, Map<String, Object> arguments) {
        if (!initialized) {
            fetchToolsList();
        }
        
        // 确定调用 URL（优先使用 endpoint，否则使用默认路径）
        String callUrl;
        if (mcpEndpoint != null && !mcpEndpoint.isEmpty()) {
            callUrl = mcpEndpoint + "/call";
        } else {
            callUrl = mcpServerUrl + "/mcp/tools/call";
        }
        
        log.info("Calling MCP tool: {}, URL: {}, arguments: {}", toolName, callUrl, arguments);

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("name", toolName);
        requestBody.set("arguments", objectMapper.valueToTree(arguments));

        return webClient.post()
            .uri(callUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .doOnSuccess(response -> log.info("MCP tool response: {}", response))
            .doOnError(error -> {
                if (error instanceof WebClientResponseException) {
                    WebClientResponseException ex = (WebClientResponseException) error;
                    log.error("MCP tool error: status={}, body={}", 
                        ex.getStatusCode(), ex.getResponseBodyAsString());
                } else {
                    log.error("MCP tool error: {}", error.getMessage());
                }
            })
            .onErrorResume(error -> {
                // 返回错误响应
                ObjectNode errorResponse = objectMapper.createObjectNode();
                errorResponse.put("success", false);
                errorResponse.put("error", error.getMessage());
                return Mono.just(errorResponse);
            });
    }

    // ==================== 环境资源管理工具 ====================

    public Mono<JsonNode> applyResource(Map<String, Object> params) {
        return callTool("applyResource", params);
    }

    public Mono<JsonNode> recycleResource(Map<String, Object> params) {
        return callTool("recycleResource", params);
    }

    // ==================== 传统测试工具 ====================

    public Mono<JsonNode> autoInterfaceTest(Map<String, Object> params) {
        return callTool("autoInterfaceTest", params);
    }

    public Mono<JsonNode> autoUITest(Map<String, Object> params) {
        return callTool("autoUITest", params);
    }

    public Mono<JsonNode> resultAnalysis(Map<String, Object> params) {
        return callTool("resultAnalysis", params);
    }

    // ==================== 测试批次管理工具 ====================

    public Mono<JsonNode> createBatch(Map<String, Object> params) {
        return callTool("createBatch", params);
    }

    public Mono<JsonNode> addCasesToBatch(Map<String, Object> params) {
        return callTool("addCasesToBatch", params);
    }

    public Mono<JsonNode> executeBatch(Map<String, Object> params) {
        return callTool("executeBatch", params);
    }

    public Mono<JsonNode> analyzeBatchResult(Map<String, Object> params) {
        return callTool("analyzeBatchResult", params);
    }

    // ==================== 记忆工具 ====================

    public Mono<JsonNode> readMemory(Map<String, Object> params) {
        return callTool("readMemory", params);
    }

    public void writeMemoryAsync(Map<String, Object> params) {
        callTool("writeMemory", params)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                result -> log.info("Memory write success: {}", result),
                error -> log.error("Memory write failed: {}", error.getMessage())
            );
    }

    public Mono<JsonNode> writeMemory(Map<String, Object> params) {
        return callTool("writeMemory", params);
    }
}
