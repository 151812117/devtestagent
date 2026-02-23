package com.example.devtestagent.agent.master;

import com.example.devtestagent.agent.sub.EnvManagementAgent;
import com.example.devtestagent.agent.sub.TestAgent;
import com.example.devtestagent.model.*;
import com.example.devtestagent.mcp.MemoryService;
import com.example.devtestagent.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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
    private final Resource systemPrompt;

    public MasterAgent(
            LlmService llmService,
            EnvManagementAgent envManagementAgent,
            TestAgent testAgent,
            MemoryService memoryService,
            @Qualifier("masterAgentSystemPrompt") Resource systemPrompt) {
        this.llmService = llmService;
        this.envManagementAgent = envManagementAgent;
        this.testAgent = testAgent;
        this.memoryService = memoryService;
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

        prompt.append("请根据用户输入判断：\n");
        prompt.append("1. 任务类型（ENV_MANAGEMENT: 环境管理, TEST_EXECUTION: 测试执行）\n");
        prompt.append("2. 目标子智能体（EnvManagementAgent / TestAgent）\n");
        prompt.append("3. 是否需要用户确认参数\n");
        prompt.append("4. 简要任务描述\n\n");
        
        return prompt.toString();
    }

    /**
     * 解析任务规划结果
     */
    private TaskPlan parseTaskPlan(String planResult, String userQuery) {
        // 基于关键词匹配进行简单解析
        String lowerQuery = userQuery.toLowerCase();

        TaskPlan.TaskType taskType;
        String targetAgent;

        if (lowerQuery.contains("环境") || lowerQuery.contains("资源") || 
            lowerQuery.contains("申请") || lowerQuery.contains("回收") ||
            lowerQuery.contains("env") || lowerQuery.contains("resource")) {
            taskType = TaskPlan.TaskType.ENV_MANAGEMENT;
            targetAgent = "EnvManagementAgent";
        } else if (lowerQuery.contains("测试") || lowerQuery.contains("接口") || 
                   lowerQuery.contains("界面") || lowerQuery.contains("test") ||
                   lowerQuery.contains("api")) {
            taskType = TaskPlan.TaskType.TEST_EXECUTION;
            targetAgent = "TestAgent";
        } else {
            taskType = TaskPlan.TaskType.UNKNOWN;
            targetAgent = "Unknown";
        }

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
        } else if (action.startsWith("auto") || action.startsWith("resultAnalysis")) {
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
