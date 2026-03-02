package com.example.devtestagent.agent.master;

import com.example.devtestagent.agent.sub.EnvManagementAgent;
import com.example.devtestagent.agent.sub.TestAgent;
import com.example.devtestagent.model.*;
import com.example.devtestagent.mcp.MemoryService;
import com.example.devtestagent.service.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * 主智能体（Master Agent）
 * 采用 Plan-and-Execute 模式，负责任务规划和智能路由
 */
@Slf4j
@Component
public class MasterAgent {

    private final LlmService llmService;
    private final EnvManagementAgent envManagementAgent;
    private final TestAgent testAgent;
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;
    private final Resource systemPrompt;

    public MasterAgent(
            LlmService llmService,
            EnvManagementAgent envManagementAgent,
            TestAgent testAgent,
            MemoryService memoryService,
            ObjectMapper objectMapper,
            @Qualifier("masterAgentSystemPrompt") Resource systemPrompt) {
        this.llmService = llmService;
        this.envManagementAgent = envManagementAgent;
        this.testAgent = testAgent;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
        this.systemPrompt = systemPrompt;
    }

    /**
     * 处理用户请求 - 第一阶段：意图解析
     */
    public Mono<AgentResponse> processIntentParse(String userQuery, String userId, String sessionId) {
        String requestId = generateRequestId();
        log.info("[MasterAgent] Processing intent parse, requestId: {}, query: {}", requestId, userQuery);

        // 1. 读取记忆
        return memoryService.readMemory(userId, sessionId)
            .flatMap(memory -> {
                // 2. 进行任务规划
                return planTask(userQuery, memory)
                    .flatMap(taskPlan -> {
                        log.info("[MasterAgent] Task plan: {}", taskPlan);

                        // 3. 构建子智能体请求
                        AgentRequest subAgentRequest = AgentRequest.builder()
                            .requestId(requestId)
                            .userQuery(userQuery)
                            .userId(userId)
                            .sessionId(sessionId)
                            .phase(AgentRequest.RequestPhase.INTENT_PARSE)
                            .memory(formatMemory(memory))
                            .build();

                        // 4. 路由到子智能体进行意图解析
                        return routeToSubAgentForIntentParse(taskPlan, subAgentRequest);
                    });
            })
            .map(intentResult -> AgentResponse.builder()
                .responseId(generateRequestId())
                .requestId(requestId)
                .type(AgentResponse.ResponseType.INTENT_PARSE_RESULT)
                .intentResult(intentResult)
                .build())
            .onErrorResume(error -> {
                log.error("[MasterAgent] Intent parse error: {}", error.getMessage());
                return Mono.just(AgentResponse.builder()
                    .responseId(generateRequestId())
                    .requestId(requestId)
                    .type(AgentResponse.ResponseType.ERROR)
                    .errorMessage(error.getMessage())
                    .build());
            });
    }

    /**
     * 处理用户请求 - 第二阶段：任务执行
     */
    public Mono<AgentResponse> processExecution(String requestId, String userQuery, 
                                                  IntentParseIntent confirmedParams,
                                                  String userId, String sessionId) {
        log.info("[MasterAgent] Processing execution, requestId: {}, query: {}", requestId, userQuery);

        // 构建子智能体请求
        AgentRequest subAgentRequest = AgentRequest.builder()
            .requestId(requestId)
            .userQuery(userQuery)
            .userId(userId)
            .sessionId(sessionId)
            .phase(AgentRequest.RequestPhase.EXECUTION)
            .parameters(confirmedParams.getParameters())
            .build();

        // 根据 action 类型路由到对应的子智能体
        return routeToSubAgentForExecution(confirmedParams.getAction(), subAgentRequest)
            .map(executionResult -> AgentResponse.builder()
                .responseId(generateRequestId())
                .requestId(requestId)
                .type(AgentResponse.ResponseType.EXECUTION_RESULT)
                .executionResult(executionResult)
                .build())
            .onErrorResume(error -> {
                log.error("[MasterAgent] Execution error: {}", error.getMessage());
                return Mono.just(AgentResponse.builder()
                    .responseId(generateRequestId())
                    .requestId(requestId)
                    .type(AgentResponse.ResponseType.ERROR)
                    .errorMessage(error.getMessage())
                    .build());
            });
    }

    /**
     * 任务规划 - Plan-and-Execute 模式的核心
     */
    private Mono<TaskPlan> planTask(String userQuery, MemoryContext memory) {
        String planPrompt = buildPlanningPrompt(userQuery, memory);
        String systemPromptContent = getSystemPromptContent();

        return llmService.generate(systemPromptContent, planPrompt)
            .map(planResult -> {
                log.info("[MasterAgent] LLM plan result: {}", planResult);
                return parseTaskPlan(planResult, userQuery);
            });
    }

    /**
     * 构建规划提示
     */
    private String buildPlanningPrompt(String userQuery, MemoryContext memory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请分析以下用户需求，进行任务规划：\n\n");
        prompt.append("用户输入：").append(userQuery).append("\n\n");
        
        if (memory != null && memory.getHistoryTasks() != null && !memory.getHistoryTasks().isEmpty()) {
            prompt.append("用户历史记录：\n");
            memory.getHistoryTasks().forEach(task -> 
                prompt.append("- ").append(task.getAction()).append(" ").append(task.getTarget()).append("\n")
            );
            prompt.append("\n");
        }

        prompt.append("可用的子智能体：\n");
        prompt.append("- EnvManagementAgent: 环境管理（申请/回收环境资源）\n");
        prompt.append("- TestAgent: 测试执行（创建批次、添加案例、执行测试、结果分析）\n\n");
        
        prompt.append("请根据用户输入，以 JSON 格式返回任务规划结果：\n");
        prompt.append("{\n");
        prompt.append("  \"taskType\": \"ENV_MANAGEMENT 或 TEST_EXECUTION\",\n");
        prompt.append("  \"targetAgent\": \"EnvManagementAgent 或 TestAgent\",\n");
        prompt.append("  \"description\": \"任务描述\",\n");
        prompt.append("  \"needConfirmation\": true\n");
        prompt.append("}\n");
        
        return prompt.toString();
    }

    /**
     * 解析任务规划结果
     */
    private TaskPlan parseTaskPlan(String planResult, String userQuery) {
        try {
            // 尝试从 JSON 中解析
            String jsonStr = extractJson(planResult);
            JsonNode jsonNode = objectMapper.readTree(jsonStr);
            
            // 解析 taskType
            String taskTypeStr = jsonNode.has("taskType") ? jsonNode.get("taskType").asText() : "UNKNOWN";
            TaskPlan.TaskType taskType;
            try {
                taskType = TaskPlan.TaskType.valueOf(taskTypeStr);
            } catch (IllegalArgumentException e) {
                // 如果解析失败，使用关键词回退
                taskType = fallbackTaskType(userQuery);
            }
            
            // 解析 targetAgent
            String targetAgent = jsonNode.has("targetAgent") ? jsonNode.get("targetAgent").asText() : null;
            if (targetAgent == null || targetAgent.isEmpty()) {
                targetAgent = fallbackTargetAgent(userQuery, taskType);
            }
            
            // 解析其他字段
            String description = jsonNode.has("description") ? jsonNode.get("description").asText() : planResult;
            boolean needConfirmation = jsonNode.has("needConfirmation") ? jsonNode.get("needConfirmation").asBoolean() : true;
            
            return TaskPlan.builder()
                .taskType(taskType)
                .targetAgent(targetAgent)
                .description(description)
                .needConfirmation(needConfirmation)
                .build();
                
        } catch (Exception e) {
            log.warn("Failed to parse planResult as JSON, using fallback: {}", e.getMessage());
            // 解析失败时使用关键词回退
            return fallbackParseTaskPlan(userQuery, planResult);
        }
    }
    
    /**
     * 从字符串中提取 JSON
     */
    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
    
    /**
     * 回退：基于关键词匹配 taskType
     */
    private TaskPlan.TaskType fallbackTaskType(String userQuery) {
        String lowerQuery = userQuery.toLowerCase();
        
        if (lowerQuery.contains("环境") || lowerQuery.contains("资源") || 
            lowerQuery.contains("申请") || lowerQuery.contains("回收") ||
            lowerQuery.contains("env") || lowerQuery.contains("resource")) {
            return TaskPlan.TaskType.ENV_MANAGEMENT;
        } else if (lowerQuery.contains("测试") || lowerQuery.contains("接口") || 
                   lowerQuery.contains("界面") || lowerQuery.contains("test") ||
                   lowerQuery.contains("api") || lowerQuery.contains("批次") ||
                   lowerQuery.contains("案例") || lowerQuery.contains("执行")) {
            return TaskPlan.TaskType.TEST_EXECUTION;
        }
        return TaskPlan.TaskType.UNKNOWN;
    }
    
    /**
     * 回退：基于关键词匹配 targetAgent
     */
    private String fallbackTargetAgent(String userQuery, TaskPlan.TaskType taskType) {
        if (taskType == TaskPlan.TaskType.ENV_MANAGEMENT) {
            return "EnvManagementAgent";
        } else if (taskType == TaskPlan.TaskType.TEST_EXECUTION) {
            return "TestAgent";
        }
        
        // 再试试关键词
        String lowerQuery = userQuery.toLowerCase();
        if (lowerQuery.contains("环境") || lowerQuery.contains("资源")) {
            return "EnvManagementAgent";
        }
        return "TestAgent";
    }
    
    /**
     * 完全回退解析
     */
    private TaskPlan fallbackParseTaskPlan(String userQuery, String planResult) {
        TaskPlan.TaskType taskType = fallbackTaskType(userQuery);
        String targetAgent = fallbackTargetAgent(userQuery, taskType);
        
        return TaskPlan.builder()
            .taskType(taskType)
            .targetAgent(targetAgent)
            .description(planResult)
            .needConfirmation(true)
            .build();
    }

    /**
     * 路由到子智能体进行意图解析
     */
    private Mono<IntentParseResult> routeToSubAgentForIntentParse(TaskPlan taskPlan, AgentRequest request) {
        switch (taskPlan.getTaskType()) {
            case ENV_MANAGEMENT:
                return envManagementAgent.parseIntent(request);
            case TEST_EXECUTION:
                return testAgent.parseIntent(request);
            default:
                return Mono.error(new IllegalArgumentException("Unknown task type: " + taskPlan.getTaskType()));
        }
    }

    /**
     * 路由到子智能体进行执行
     */
    private Mono<AgentResponse.ExecutionResult> routeToSubAgentForExecution(String action, AgentRequest request) {
        if (action.startsWith("applyResource") || action.startsWith("recycleResource")) {
            return envManagementAgent.execute(request);
        } else if (action.startsWith("auto") || action.startsWith("resultAnalysis") ||
                   action.startsWith("createBatch") || action.startsWith("addCasesToBatch") ||
                   action.startsWith("executeBatch") || action.startsWith("analyzeBatchResult")) {
            return testAgent.execute(request);
        } else {
            return Mono.error(new IllegalArgumentException("Unknown action: " + action));
        }
    }

    /**
     * 格式化记忆内容
     */
    private String formatMemory(MemoryContext memory) {
        if (memory == null) {
            return "无历史记录";
        }
        
        StringBuilder sb = new StringBuilder();
        if (memory.getRecentEnvOperations() != null && !memory.getRecentEnvOperations().isEmpty()) {
            sb.append("最近环境操作：\n");
            memory.getRecentEnvOperations().forEach(op -> 
                sb.append("- ").append(op.getAction()).append(" ").append(op.getTarget()).append("\n")
            );
        }
        if (memory.getRecentTestRecords() != null && !memory.getRecentTestRecords().isEmpty()) {
            sb.append("最近测试记录：\n");
            memory.getRecentTestRecords().forEach(record -> 
                sb.append("- ").append(record.getAction()).append(" ").append(record.getTarget()).append("\n")
            );
        }
        return sb.length() > 0 ? sb.toString() : "无相关历史记录";
    }

    private String getSystemPromptContent() {
        try {
            return new String(systemPrompt.getInputStream().readAllBytes());
        } catch (Exception e) {
            log.error("Failed to read system prompt", e);
            return "你是一个智能任务规划助手。";
        }
    }

    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // 内部类用于接收确认参数
    public interface IntentParseIntent {
        String getAction();
        Map<String, Object> getParameters();
    }
}
