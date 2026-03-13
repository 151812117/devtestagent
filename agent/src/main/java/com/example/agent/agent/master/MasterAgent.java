package com.example.agent.agent.master;

import com.example.agent.agent.sub.EnvManagementAgent;
import com.example.agent.agent.sub.MenuRecommendationAgent;
import com.example.agent.agent.sub.TestAgent;
import com.example.agent.model.*;
import com.example.agent.service.LlmService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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
    private final MenuRecommendationAgent menuRecommendationAgent;
    private final ObjectMapper objectMapper;
    private final Resource systemPrompt;

    public MasterAgent(
            LlmService llmService,
            EnvManagementAgent envManagementAgent,
            TestAgent testAgent,
            MenuRecommendationAgent menuRecommendationAgent,
            ObjectMapper objectMapper,
            @Qualifier("masterAgentSystemPrompt") Resource systemPrompt) {
        this.llmService = llmService;
        this.envManagementAgent = envManagementAgent;
        this.testAgent = testAgent;
        this.menuRecommendationAgent = menuRecommendationAgent;
        this.objectMapper = objectMapper;
        this.systemPrompt = systemPrompt;
    }

    /**
     * 处理意图解析
     */
    public Mono<IntentParseResult> processIntentParse(AgentRequest request) {
        log.info("[MasterAgent] Processing intent parse, requestId: {}", request.getRequestId());

        // 1. 进行任务规划
        return planTask(request.getUserQuery(), request.getMemory())
            .flatMap(taskPlan -> {
                log.info("[MasterAgent] Task plan: {}", taskPlan);

                // 2. 路由到子智能体进行意图解析
                return routeToSubAgentForIntentParse(taskPlan, request);
            });
    }

    /**
     * 处理任务执行
     */
    public Mono<ExecutionResult> processExecution(String action, AgentRequest request) {
        log.info("[MasterAgent] Processing execution, requestId: {}, action: {}", 
            request.getRequestId(), action);

        // 路由到子智能体进行执行
        return routeToSubAgentForExecution(action, request);
    }

    /**
     * 任务规划
     */
    private Mono<TaskPlan> planTask(String userQuery, String memory) {
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
    private String buildPlanningPrompt(String userQuery, String memory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请分析以下用户需求，进行任务规划：\n\n");
        prompt.append("用户输入：").append(userQuery).append("\n\n");
        
        if (memory != null && !memory.isEmpty() && !"无相关历史记录".equals(memory)) {
            prompt.append("用户历史记录：\n").append(memory).append("\n\n");
        }

        prompt.append("可用的子智能体：\n");
        prompt.append("- EnvManagementAgent: 环境管理（申请/回收环境资源）\n");
        prompt.append("- TestAgent: 测试执行（创建批次、添加案例、执行测试、结果分析）\n");
        prompt.append("- MenuRecommendationAgent: 菜单推荐（帮助用户找到合适的系统功能菜单）\n\n");
        
        prompt.append("请根据用户输入，以 JSON 格式返回任务规划结果：\n");
        prompt.append("{\n");
        prompt.append("  \"taskType\": \"ENV_MANAGEMENT 或 TEST_EXECUTION 或 MENU_RECOMMENDATION\",\n");
        prompt.append("  \"targetAgent\": \"EnvManagementAgent 或 TestAgent 或 MenuRecommendationAgent\",\n");
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
            String jsonStr = extractJson(planResult);
            JsonNode jsonNode = objectMapper.readTree(jsonStr);
            
            String taskTypeStr = jsonNode.has("taskType") ? jsonNode.get("taskType").asText() : "UNKNOWN";
            TaskPlan.TaskType taskType;
            try {
                taskType = TaskPlan.TaskType.valueOf(taskTypeStr);
            } catch (IllegalArgumentException e) {
                taskType = fallbackTaskType(userQuery);
            }
            
            String targetAgent = jsonNode.has("targetAgent") ? jsonNode.get("targetAgent").asText() : null;
            if (targetAgent == null || targetAgent.isEmpty()) {
                targetAgent = fallbackTargetAgent(userQuery, taskType);
            }
            
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
            return fallbackParseTaskPlan(userQuery, planResult);
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private TaskPlan.TaskType fallbackTaskType(String userQuery) {
        String lowerQuery = userQuery.toLowerCase();
        
        // 菜单推荐关键词
        if (lowerQuery.contains("菜单") || lowerQuery.contains("推荐") || 
            lowerQuery.contains("功能") || lowerQuery.contains("入口") ||
            lowerQuery.contains("在哪") || lowerQuery.contains("怎么") ||
            lowerQuery.contains("如何") || lowerQuery.contains("哪里")) {
            return TaskPlan.TaskType.MENU_RECOMMENDATION;
        } else if (lowerQuery.contains("环境") || lowerQuery.contains("资源") || 
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

    private String fallbackTargetAgent(String userQuery, TaskPlan.TaskType taskType) {
        if (taskType == TaskPlan.TaskType.MENU_RECOMMENDATION) {
            return "MenuRecommendationAgent";
        } else if (taskType == TaskPlan.TaskType.ENV_MANAGEMENT) {
            return "EnvManagementAgent";
        } else if (taskType == TaskPlan.TaskType.TEST_EXECUTION) {
            return "TestAgent";
        }
        
        String lowerQuery = userQuery.toLowerCase();
        if (lowerQuery.contains("菜单") || lowerQuery.contains("推荐") ||
            lowerQuery.contains("在哪") || lowerQuery.contains("怎么")) {
            return "MenuRecommendationAgent";
        } else if (lowerQuery.contains("环境") || lowerQuery.contains("资源")) {
            return "EnvManagementAgent";
        }
        return "TestAgent";
    }

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

    private Mono<IntentParseResult> routeToSubAgentForIntentParse(TaskPlan taskPlan, AgentRequest request) {
        switch (taskPlan.getTaskType()) {
            case ENV_MANAGEMENT:
                return envManagementAgent.parseIntent(request);
            case TEST_EXECUTION:
                return testAgent.parseIntent(request);
            case MENU_RECOMMENDATION:
                return menuRecommendationAgent.parseIntent(request);
            default:
                return Mono.error(new IllegalArgumentException("Unknown task type: " + taskPlan.getTaskType()));
        }
    }

    private Mono<ExecutionResult> routeToSubAgentForExecution(String action, AgentRequest request) {
        if (action.startsWith("applyResource") || action.startsWith("recycleResource")) {
            return envManagementAgent.execute(request);
        } else if (action.startsWith("auto") || action.startsWith("resultAnalysis") ||
                   action.startsWith("createBatch") || action.startsWith("addCasesToBatch") ||
                   action.startsWith("executeBatch") || action.startsWith("analyzeBatchResult")) {
            return testAgent.execute(request);
        } else if (action.startsWith("recommendMenu") || action.startsWith("menuRecommendation")) {
            return menuRecommendationAgent.execute(request);
        } else {
            return Mono.error(new IllegalArgumentException("Unknown action: " + action));
        }
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
}
