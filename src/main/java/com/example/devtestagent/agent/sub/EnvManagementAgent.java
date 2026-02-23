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
 * 研发环境管理智能体
 * 采用 ReAct 模式，处理环境资源申请和回收
 */
@Slf4j
@Component
public class EnvManagementAgent implements SubAgent {

    private final LlmService llmService;
    private final McpToolClient mcpToolClient;
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;
    private final Resource systemPrompt;

    public EnvManagementAgent(
            LlmService llmService,
            McpToolClient mcpToolClient,
            MemoryService memoryService,
            ObjectMapper objectMapper,
            @Qualifier("envAgentSystemPrompt") Resource systemPrompt) {
        this.llmService = llmService;
        this.mcpToolClient = mcpToolClient;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String getName() {
        return "EnvManagementAgent";
    }

    @Override
    public Mono<IntentParseResult> parseIntent(AgentRequest request) {
        log.info("[EnvManagementAgent] Parsing intent for query: {}", request.getUserQuery());

        // ReAct 模式：思考 -> 行动 -> 观察
        return Mono.fromCallable(() -> {
            // 1. 构建提示词
            String prompt = buildParsePrompt(request);
            String systemPromptContent = getSystemPromptContent();
            
            // 2. 调用 LLM 解析意图
            String response = llmService.generate(systemPromptContent, prompt).block();

            log.info("[EnvManagementAgent] LLM response: {}", response);

            // 3. 解析 LLM 输出为 JSON
            return parseLLMResponse(response, request.getUserQuery());
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    @Override
    public Mono<AgentResponse.ExecutionResult> execute(AgentRequest request) {
        String action = getActionFromRequest(request);
        log.info("[EnvManagementAgent] Executing action: {}", action);

        Map<String, Object> params = request.getParameters() != null ? 
            request.getParameters() : Collections.emptyMap();

        Mono<JsonNode> resultMono;

        switch (action) {
            case "applyResource":
                resultMono = mcpToolClient.applyResource(params);
                break;
            case "recycleResource":
                resultMono = mcpToolClient.recycleResource(params);
                break;
            default:
                return Mono.error(new IllegalArgumentException("Unknown action: " + action));
        }

        final String finalAction = action;
        return resultMono.map(result -> {
            // 异步写入记忆
            memoryService.recordEnvOperation(
                request.getUserId(),
                request.getSessionId(),
                finalAction,
                params.getOrDefault("envType", "unknown").toString(),
                result.toString()
            );

            return AgentResponse.ExecutionResult.builder()
                .success(true)
                .message("Environment operation completed successfully")
                .data(result)
                .taskId(UUID.randomUUID().toString())
                .build();
        }).onErrorResume(error -> {
            log.error("[EnvManagementAgent] Execution error: {}", error.getMessage());
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
        userPrompt.append("请解析以下用户需求，提取关键参数：\n\n");
        userPrompt.append("用户输入：").append(request.getUserQuery()).append("\n\n");
        
        if (request.getMemory() != null && !request.getMemory().isEmpty()) {
            userPrompt.append("历史记忆：\n").append(request.getMemory()).append("\n\n");
        }

        userPrompt.append("请根据用户输入判断：\n");
        userPrompt.append("1. 操作类型（applyResource/recycleResource）\n");
        userPrompt.append("2. 环境类型（development/testing/staging/production）\n");
        userPrompt.append("3. 其他相关参数\n\n");
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

            Map<String, String> paramDescriptions = new HashMap<>();
            paramDescriptions.put("envType", "环境类型");
            paramDescriptions.put("duration", "使用时长（小时）");
            paramDescriptions.put("cpu", "CPU核心数");
            paramDescriptions.put("memory", "内存大小（GB）");
            paramDescriptions.put("storage", "存储大小（GB）");
            paramDescriptions.put("instanceCount", "实例数量");
            paramDescriptions.put("purpose", "使用目的");
            paramDescriptions.put("owner", "申请人");
            paramDescriptions.put("envId", "环境ID");
            paramDescriptions.put("resourceId", "资源ID");
            paramDescriptions.put("recycleType", "回收类型");
            paramDescriptions.put("reason", "回收原因");

            return IntentParseResult.builder()
                .action(action)
                .target(target)
                .parameters(parameters)
                .parameterDescriptions(paramDescriptions)
                .confidence(0.9)
                .originalQuery(originalQuery)
                .needConfirmation(true)
                .confirmationMessage("请确认以上环境操作参数是否正确")
                .build();

        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", e.getMessage());
            // 返回默认解析结果
            return createDefaultParseResult(originalQuery);
        }
    }

    /**
     * 从响应中提取 JSON
     */
    private String extractJson(String response) {
        // 尝试找到 JSON 块
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
        if ("applyResource".equals(action)) {
            parameters.putIfAbsent("duration", 24);
            parameters.putIfAbsent("cpu", 4);
            parameters.putIfAbsent("memory", 8);
            parameters.putIfAbsent("storage", 100);
            parameters.putIfAbsent("instanceCount", 1);
        }
    }

    /**
     * 创建默认解析结果
     */
    private IntentParseResult createDefaultParseResult(String originalQuery) {
        Map<String, Object> parameters = new HashMap<>();
        
        // 基于关键词判断
        String lowerQuery = originalQuery.toLowerCase();
        String action = "applyResource";
        
        if (lowerQuery.contains("回收") || lowerQuery.contains("释放") || lowerQuery.contains("删除")) {
            action = "recycleResource";
        }

        if (lowerQuery.contains("开发") || lowerQuery.contains("dev")) {
            parameters.put("envType", "development");
        } else if (lowerQuery.contains("测试") || lowerQuery.contains("test")) {
            parameters.put("envType", "testing");
        } else if (lowerQuery.contains("预发") || lowerQuery.contains("staging")) {
            parameters.put("envType", "staging");
        } else {
            parameters.put("envType", "development");
        }

        setDefaultValues(action, parameters);

        return IntentParseResult.builder()
            .action(action)
            .target(parameters.get("envType").toString())
            .parameters(parameters)
            .confidence(0.7)
            .originalQuery(originalQuery)
            .needConfirmation(true)
            .confirmationMessage("已根据您的输入提取参数，请确认或修改")
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
        String lowerQuery = request.getUserQuery().toLowerCase();
        if (lowerQuery.contains("回收") || lowerQuery.contains("释放") || lowerQuery.contains("删除")) {
            return "recycleResource";
        }
        return "applyResource";
    }

    private String getSystemPromptContent() {
        try {
            return new String(systemPrompt.getInputStream().readAllBytes());
        } catch (Exception e) {
            log.error("Failed to read system prompt", e);
            return "你是一个研发环境管理助手。";
        }
    }
}
