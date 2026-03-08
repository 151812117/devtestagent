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
            // 传统测试工具
            case "autoInterfaceTest":
                resultMono = mcpToolClient.autoInterfaceTest(params);
                break;
            case "autoUITest":
                resultMono = mcpToolClient.autoUITest(params);
                break;
            case "resultAnalysis":
                resultMono = mcpToolClient.resultAnalysis(params);
                break;
            // 测试批次管理工具
            case "createBatch":
                resultMono = mcpToolClient.createBatch(params);
                break;
            case "addCasesToBatch":
                // 确保 caseIds 是列表格式
                Map<String, Object> addCasesParams = convertCaseIdsToList(new java.util.HashMap<>(params));
                resultMono = mcpToolClient.addCasesToBatch(addCasesParams);
                break;
            case "executeBatch":
                resultMono = mcpToolClient.executeBatch(params);
                break;
            case "analyzeBatchResult":
                resultMono = mcpToolClient.analyzeBatchResult(params);
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
                params.getOrDefault("testScenario", params.getOrDefault("systemName", "unknown")).toString(),
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

        userPrompt.append("请根据用户输入判断操作类型：\n");
        userPrompt.append("可用的操作类型：\n");
        userPrompt.append("- createBatch: 创建测试批次（需要：systemName, systemCode, batchLabel）\n");
        userPrompt.append("- addCasesToBatch: 添加案例到批次（需要：caseIds, batchId）\n");
        userPrompt.append("- executeBatch: 执行批次（需要：batchId, systemCode）\n");
        userPrompt.append("- analyzeBatchResult: 批次结果分析（需要：executionId）\n");
        userPrompt.append("- autoInterfaceTest: 接口测试\n");
        userPrompt.append("- autoUITest: 界面测试\n");
        userPrompt.append("- resultAnalysis: 测试结果分析（只需要testId）\n\n");
        
        userPrompt.append("请以 JSON 格式返回解析结果，包含以下字段：\n");
        userPrompt.append("- action: 操作类型\n");
        userPrompt.append("- target: 操作目标\n");
        userPrompt.append("- parameters: 提取的参数\n");
        userPrompt.append("- think: 你的思考过程，包括对用户意图的理解、提取了哪些参数、哪些参数缺失需要用户补充\n");
        userPrompt.append("- missingParameters: 需要补充的参数名和说明\n");

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

            String action = jsonNode.has("action") ? jsonNode.get("action").asText().trim() : "unknown";
            String target = jsonNode.has("target") ? jsonNode.get("target").asText().trim() : "unknown";
            
            Map<String, Object> parameters = new HashMap<>();
            if (jsonNode.has("parameters")) {
                parameters = objectMapper.convertValue(jsonNode.get("parameters"), 
                    new TypeReference<Map<String, Object>>() {});
            }
            
            // 提取思考过程
            String think = jsonNode.has("think") ? jsonNode.get("think").asText() : "";
            
            // 提取需要补充的参数
            Map<String, String> missingParameters = new HashMap<>();
            if (jsonNode.has("missingParameters")) {
                missingParameters = objectMapper.convertValue(jsonNode.get("missingParameters"), 
                    new TypeReference<Map<String, String>>() {});
            }

            // 设置默认值
            setDefaultValues(action, parameters);

            Map<String, String> paramDescriptions = buildParameterDescriptions(action);
            
            // 根据缺失参数数量判断确认消息
            String confirmationMessage;
            if (missingParameters.isEmpty()) {
                confirmationMessage = "请确认以上测试参数是否正确";
            } else if (missingParameters.size() == 1) {
                String paramName = missingParameters.keySet().iterator().next();
                String paramDesc = missingParameters.get(paramName);
                confirmationMessage = "请补充" + paramDesc + "信息";
            } else {
                confirmationMessage = "请补充以下参数信息";
            }

            return IntentParseResult.builder()
                .action(action)
                .target(target)
                .parameters(parameters)
                .parameterDescriptions(paramDescriptions)
                .confidence(0.9)
                .originalQuery(originalQuery)
                .needConfirmation(true)
                .confirmationMessage(confirmationMessage)
                .think(think)
                .missingParameters(missingParameters)
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
            // 传统测试工具
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
                descriptions.put("testId", "测试ID（必填）");
                break;
            // 测试批次管理工具
            case "createBatch":
                descriptions.put("systemName", "被测系统名");
                descriptions.put("systemCode", "被测系统编号");
                descriptions.put("batchLabel", "批次标识");
                break;
            case "addCasesToBatch":
                descriptions.put("caseIds", "案例编号列表");
                descriptions.put("batchId", "批次编号");
                break;
            case "executeBatch":
                descriptions.put("batchId", "批次编号");
                descriptions.put("systemCode", "系统编号");
                break;
            case "analyzeBatchResult":
                descriptions.put("executionId", "执行编号");
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
                // resultAnalysis 只需要 testId 参数
                break;
            // 测试批次工具不需要默认值
            case "createBatch":
            case "addCasesToBatch":
            case "executeBatch":
            case "analyzeBatchResult":
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
        
        // 判断测试类型 - 优先检查批次相关操作
        if (lowerQuery.contains("创建批次") || lowerQuery.contains("新建批次") || 
            lowerQuery.contains("创建测试")) {
            action = "createBatch";
        } else if (lowerQuery.contains("添加案例") || lowerQuery.contains("加入案例")) {
            action = "addCasesToBatch";
        } else if (lowerQuery.contains("执行批次") || lowerQuery.contains("运行批次")) {
            action = "executeBatch";
        } else if (lowerQuery.contains("分析结果") || lowerQuery.contains("批次分析") ||
                   lowerQuery.contains("执行编号")) {
            action = "analyzeBatchResult";
        } else if (lowerQuery.contains("界面") || lowerQuery.contains("ui") || 
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
            .think("从用户输入中提取了基本参数，请确认或补充缺失信息")
            .missingParameters(new HashMap<>())
            .build();
    }

    /**
     * 从请求中获取操作类型
     */
    private String getActionFromRequest(AgentRequest request) {
        if (request.getParameters() != null && request.getParameters().containsKey("action")) {
            String action = request.getParameters().get("action").toString().trim();
            log.debug("[TestAgent] Getting action from request: '{}'", action);
            return action;
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

    /**
     * 将 caseIds 转换为 List<String> 格式
     */
    private Map<String, Object> convertCaseIdsToList(Map<String, Object> params) {
        if (params == null) {
            return params;
        }
        
        Object caseIds = params.get("caseIds");
        if (caseIds == null) {
            return params;
        }
        
        // 如果已经是 List，直接返回
        if (caseIds instanceof java.util.List) {
            return params;
        }
        
        // 如果是 String，转换为 List
        if (caseIds instanceof String) {
            String caseIdStr = (String) caseIds;
            // 尝试解析 JSON 数组格式 ["case001", "case002"]
            try {
                if (caseIdStr.trim().startsWith("[")) {
                    java.util.List<String> list = objectMapper.readValue(caseIdStr, 
                        objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, String.class));
                    params.put("caseIds", list);
                    log.info("[TestAgent] Converted caseIds from JSON array string to List: {}", list);
                } else {
                    // 单个案例 ID，包装成列表
                    java.util.List<String> list = java.util.Collections.singletonList(caseIdStr);
                    params.put("caseIds", list);
                    log.info("[TestAgent] Converted single caseId to List: {}", list);
                }
            } catch (Exception e) {
                // 解析失败，按逗号分割
                java.util.List<String> list = java.util.Arrays.stream(caseIdStr.split("[,，]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
                params.put("caseIds", list);
                log.info("[TestAgent] Converted caseIds by splitting: {}", list);
            }
        }
        
        return params;
    }
}
