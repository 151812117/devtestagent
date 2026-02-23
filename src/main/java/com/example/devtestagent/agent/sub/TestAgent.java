package com.example.devtestagent.agent.sub;

import com.example.devtestagent.model.AgentRequest;
import com.example.devtestagent.model.AgentResponse;
import com.example.devtestagent.model.IntentParseResult;
import com.example.devtestagent.mcp.McpToolClient;
import com.example.devtestagent.mcp.MemoryService;
import com.example.devtestagent.service.LlmService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 测试智能体
 * 采用 ReAct 模式，处理自动化测试和结果分析
 */
@Slf4j
@Component
public class TestAgent implements SubAgent {

    private final LlmService llmService;
    private final McpToolClient mcpToolClient;
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;
    private final Resource systemPrompt;

    public TestAgent(
            LlmService llmService,
            McpToolClient mcpToolClient,
            MemoryService memoryService,
            ObjectMapper objectMapper,
            @Qualifier("testAgentSystemPrompt") Resource systemPrompt) {
        this.llmService = llmService;
        this.mcpToolClient = mcpToolClient;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String getName() {
        return "TestAgent";
    }

    @Override
    public Mono<IntentParseResult> parseIntent(AgentRequest request) {
        log.info("[TestAgent] Parsing intent for query: {}", request.getUserQuery());

        return Mono.fromCallable(() -> {
            // 1. 构建提示词
            String prompt = buildParsePrompt(request);
            String systemPromptContent = getSystemPromptContent();
            
            // 2. 调用 LLM 解析意图
            String response = llmService.generate(systemPromptContent, prompt).block();

            log.info("[TestAgent] LLM response: {}", response);

            // 3. 解析 LLM 输出为 JSON
            return parseLLMResponse(response, request.getUserQuery());
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    @Override
    public Mono<AgentResponse.ExecutionResult> execute(AgentRequest request) {
        String action = getActionFromRequest(request);
        log.info("[TestAgent] Executing action: {}", action);

        Map<String, Object> params = request.getParameters() != null ? 
            request.getParameters() : Collections.emptyMap();

        Mono<JsonNode> resultMono;

        switch (action) {
            case "autoInterfaceTest":
                resultMono = mcpToolClient.autoInterfaceTest(params);
                break;
            case "autoUITest":
                resultMono = mcpToolClient.autoUITest(params);
                break;
            case "resultAnalysis":
                resultMono = mcpToolClient.resultAnalysis(params);
                break;
            default:
                return Mono.error(new IllegalArgumentException("Unknown action: " + action));
        }

        final String finalAction = action;
        return resultMono.map(result -> {
            // 异步写入记忆
            memoryService.recordTestOperation(
                request.getUserId(),
                request.getSessionId(),
                finalAction,
                params.getOrDefault("testScenario", "unknown").toString(),
                result.toString()
            );

            return AgentResponse.ExecutionResult.builder()
                .success(true)
                .message("Test operation completed successfully")
                .data(result)
                .taskId(UUID.randomUUID().toString())
                .build();
        }).onErrorResume(error -> {
            log.error("[TestAgent] Execution error: {}", error.getMessage());
            return Mono.just(AgentResponse.ExecutionResult.builder()
                .success(false)
                .message("Execution failed: " + error.getMessage())
                .build());
        });
    }

    /**
     * 构建意图解析提示词
     */
    private String buildParsePrompt(AgentRequest request) {
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请解析以下用户需求，提取测试相关参数：\n\n");
        userPrompt.append("用户输入：").append(request.getUserQuery()).append("\n\n");
        
        if (request.getMemory() != null && !request.getMemory().isEmpty()) {
            userPrompt.append("历史记忆：\n").append(request.getMemory()).append("\n\n");
        }

        userPrompt.append("请根据用户输入判断：\n");
        userPrompt.append("1. 测试操作类型（autoInterfaceTest/autoUITest/resultAnalysis）\n");
        userPrompt.append("2. 测试目标类型（api/graphql/grpc/websocket/web/mobile/desktop）\n");
        userPrompt.append("3. 目标URL或应用路径\n");
        userPrompt.append("4. 测试场景和用例\n");
        userPrompt.append("5. 其他配置参数\n\n");
        userPrompt.append("请以 JSON 格式返回解析结果。");

        return userPrompt.toString();
    }

    /**
     * 解析 LLM 响应
     */
    private IntentParseResult parseLLMResponse(String response, String originalQuery) {
        try {
            // 尝试提取 JSON 部分
            String jsonStr = extractJson(response);
            JsonNode jsonNode = objectMapper.readTree(jsonStr);

            String action = jsonNode.has("action") ? jsonNode.get("action").asText() : "unknown";
            String target = jsonNode.has("target") ? jsonNode.get("target").asText() : "unknown";
            
            Map<String, Object> parameters = new HashMap<>();
            if (jsonNode.has("parameters")) {
                parameters = objectMapper.convertValue(jsonNode.get("parameters"), 
                    new TypeReference<Map<String, Object>>() {});
            }

            // 设置默认值
            setDefaultValues(action, parameters);

            Map<String, String> paramDescriptions = buildParameterDescriptions(action);

            return IntentParseResult.builder()
                .action(action)
                .target(target)
                .parameters(parameters)
                .parameterDescriptions(paramDescriptions)
                .confidence(0.9)
                .originalQuery(originalQuery)
                .needConfirmation(true)
                .confirmationMessage("请确认以上测试参数是否正确")
                .build();

        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", e.getMessage());
            return createDefaultParseResult(originalQuery);
        }
    }

    /**
     * 构建参数说明
     */
    private Map<String, String> buildParameterDescriptions(String action) {
        Map<String, String> descriptions = new HashMap<>();
        
        switch (action) {
            case "autoInterfaceTest":
                descriptions.put("testType", "测试类型（api/graphql/grpc/websocket）");
                descriptions.put("targetUrl", "目标URL");
                descriptions.put("testScenario", "测试场景");
                descriptions.put("timeout", "超时时间（秒）");
                descriptions.put("concurrentUsers", "并发用户数");
                descriptions.put("requestCount", "请求数量");
                break;
            case "autoUITest":
                descriptions.put("testType", "测试类型（web/mobile/desktop）");
                descriptions.put("targetUrl", "目标URL或应用路径");
                descriptions.put("browser", "浏览器类型");
                descriptions.put("testScenario", "测试场景");
                descriptions.put("headless", "是否无头模式");
                descriptions.put("screenshotOnFailure", "失败时是否截图");
                break;
            case "resultAnalysis":
                descriptions.put("testId", "测试ID");
                descriptions.put("resultType", "结果类型（summary/detailed/performance）");
                descriptions.put("generateReport", "是否生成报告");
                break;
        }
        
        return descriptions;
    }

    /**
     * 从响应中提取 JSON
     */
    private String extractJson(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    /**
     * 设置默认值
     */
    private void setDefaultValues(String action, Map<String, Object> parameters) {
        switch (action) {
            case "autoInterfaceTest":
                parameters.putIfAbsent("testType", "api");
                parameters.putIfAbsent("timeout", 30);
                parameters.putIfAbsent("concurrentUsers", 1);
                parameters.putIfAbsent("requestCount", 10);
                break;
            case "autoUITest":
                parameters.putIfAbsent("testType", "web");
                parameters.putIfAbsent("browser", "chrome");
                parameters.putIfAbsent("headless", true);
                parameters.putIfAbsent("screenshotOnFailure", true);
                break;
            case "resultAnalysis":
                parameters.putIfAbsent("resultType", "summary");
                parameters.putIfAbsent("generateReport", true);
                break;
        }
    }

    /**
     * 创建默认解析结果
     */
    private IntentParseResult createDefaultParseResult(String originalQuery) {
        Map<String, Object> parameters = new HashMap<>();
        String action = "autoInterfaceTest";
        
        String lowerQuery = originalQuery.toLowerCase();
        
        // 判断测试类型
        if (lowerQuery.contains("界面") || lowerQuery.contains("ui") || 
            lowerQuery.contains("页面") || lowerQuery.contains("浏览器")) {
            action = "autoUITest";
            parameters.put("testType", "web");
            if (lowerQuery.contains("移动") || lowerQuery.contains("手机") || lowerQuery.contains("app")) {
                parameters.put("testType", "mobile");
            }
        } else if (lowerQuery.contains("接口") || lowerQuery.contains("api") || 
                   lowerQuery.contains("http")) {
            action = "autoInterfaceTest";
            parameters.put("testType", "api");
        } else if (lowerQuery.contains("分析") || lowerQuery.contains("报告") || 
                   lowerQuery.contains("结果")) {
            action = "resultAnalysis";
        }

        setDefaultValues(action, parameters);

        return IntentParseResult.builder()
            .action(action)
            .target(parameters.getOrDefault("testType", "unknown").toString())
            .parameters(parameters)
            .confidence(0.7)
            .originalQuery(originalQuery)
            .needConfirmation(true)
            .confirmationMessage("已根据您的输入提取测试参数，请确认或修改")
            .build();
    }

    /**
     * 从请求中获取操作类型
     */
    private String getActionFromRequest(AgentRequest request) {
        if (request.getParameters() != null && request.getParameters().containsKey("action")) {
            return request.getParameters().get("action").toString();
        }
        // 从用户查询中推断
        return createDefaultParseResult(request.getUserQuery()).getAction();
    }

    private String getSystemPromptContent() {
        try {
            return new String(systemPrompt.getInputStream().readAllBytes());
        } catch (Exception e) {
            log.error("Failed to read system prompt", e);
            return "你是一个测试管理助手。";
        }
    }
}
