package com.example.devtestagent.controller;

import com.example.devtestagent.model.AgentResponse;
import com.example.devtestagent.model.IntentParseResult;
import com.example.devtestagent.model.MemoryContext;
import com.example.devtestagent.mcp.MemoryService;
import com.example.devtestagent.service.AgentService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能体控制器
 * 处理用户请求的多轮交互
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
public class AgentController {

    private final AgentService agentService;
    private final MemoryService memoryService;

    public AgentController(AgentService agentService, MemoryService memoryService) {
        this.agentService = agentService;
        this.memoryService = memoryService;
    }

    /**
     * 第一轮交互：意图解析
     * POST /api/agent/intent
     */
    @PostMapping("/intent")
    public Mono<ResponseEntity<AgentResponse>> parseIntent(@RequestBody IntentRequest request) {
        log.info("[Controller] Intent parse request: {}", request.getQuery());

        String sessionId = request.getSessionId() != null ? 
            request.getSessionId() : agentService.generateSessionId();

        // 读取用户记忆
        return memoryService.readMemory(request.getUserId(), sessionId)
            .flatMap(memory -> {
                // 构建带记忆上下文的查询
                String queryWithMemory = buildQueryWithMemory(request.getQuery(), memory);
                log.info("[Controller] Query with memory context: {}", queryWithMemory);
                
                return agentService.processFirstRound(
                    queryWithMemory,
                    request.getUserId(),
                    sessionId,
                    memory
                );
            })
            .map(ResponseEntity::ok)
            .onErrorResume(error -> {
                log.error("[Controller] Intent parse error: {}", error.getMessage());
                return Mono.just(ResponseEntity.ok(AgentResponse.builder()
                    .type(AgentResponse.ResponseType.ERROR)
                    .errorMessage(error.getMessage())
                    .build()));
            });
    }

    /**
     * 构建带记忆上下文的查询
     */
    private String buildQueryWithMemory(String originalQuery, MemoryContext memory) {
        String memoryContent = formatMemoryContent(originalQuery, memory);
        if (memoryContent.isEmpty()) {
            return originalQuery;
        }
        return "用户操作历史：" + memoryContent + "\n\n当前需求：" + originalQuery;
    }

    /**
     * 格式化记忆内容
     * 智能选择最相关的操作记录，优先显示成功且与当前意图相关的操作
     */
    private String formatMemoryContent(String userQuery, MemoryContext memory) {
        if (memory == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        String lowerQuery = userQuery.toLowerCase();
        
        // 判断用户意图类型
        IntentType intentType = detectIntentType(lowerQuery);
        
        // 添加近期操作记录
        if (memory.getData() != null && memory.getData().get("recentOperations") != null) {
            java.util.List<?> operations = (java.util.List<?>) memory.getData().get("recentOperations");
            if (!operations.isEmpty()) {
                // 过滤并转换为Map列表
                java.util.List<java.util.Map<?, ?>> validOps = operations.stream()
                    .filter(op -> op instanceof java.util.Map)
                    .map(op -> (java.util.Map<?, ?>) op)
                    .filter(map -> map.get("action") != null)
                    .collect(Collectors.toList());
                
                if (!validOps.isEmpty()) {
                    // 按相关性排序（考虑意图匹配度和操作成功状态）
                    java.util.List<java.util.Map<?, ?>> sortedOps = sortOperationsByRelevance(validOps, intentType);
                    
                    // 选择最相关的一条（优先成功且相关的）
                    java.util.Map<?, ?> bestOp = selectBestOperation(sortedOps, intentType);
                    
                    if (bestOp != null) {
                        String action = bestOp.get("action") != null ? bestOp.get("action").toString() : "";
                        String target = bestOp.get("target") != null ? bestOp.get("target").toString() : "";
                        String resultStr = bestOp.get("result") != null ? bestOp.get("result").toString() : "";
                        
                        // 解析 result 提取关键信息（如 batchId）
                        String extractedInfo = extractKeyInfoFromResult(action, resultStr);
                        boolean isSuccess = isOperationSuccess(resultStr);
                        
                        sb.append("近期操作：");
                        sb.append(action);
                        if (!target.isEmpty()) {
                            sb.append(" ").append(target);
                        }
                        if (!extractedInfo.isEmpty()) {
                            sb.append(" (").append(extractedInfo).append(")");
                        }
                        if (!isSuccess) {
                            sb.append(" [失败]");
                        }
                        
                        // 显示相关记录数量
                        long relatedCount = sortedOps.stream()
                            .filter(op -> isRelatedOperation(op, intentType))
                            .count();
                        if (relatedCount > 1) {
                            sb.append("；相关操作共").append(relatedCount).append("条");
                        }
                    }
                }
            }
        }
        
        // 添加用户偏好
        if (memory.getUserPreference() != null) {
            MemoryContext.UserPreference pref = memory.getUserPreference();
            if (sb.length() > 0) sb.append("；");
            sb.append("用户偏好：");
            if (pref.getDefaultEnvType() != null) {
                sb.append("默认环境类型=").append(pref.getDefaultEnvType());
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 用户意图类型
     */
    private enum IntentType {
        BATCH_EXECUTION,    // 执行批次/测试
        BATCH_MANAGEMENT,   // 创建批次、添加案例
        ENV_MANAGEMENT,     // 环境申请/回收
        UNKNOWN
    }
    
    /**
     * 检测用户意图类型
     */
    private IntentType detectIntentType(String lowerQuery) {
        if (lowerQuery.contains("执行") || lowerQuery.contains("运行") || 
            lowerQuery.contains("开始") || lowerQuery.contains("启动") ||
            lowerQuery.contains("execute") || lowerQuery.contains("run")) {
            return IntentType.BATCH_EXECUTION;
        }
        if (lowerQuery.contains("创建") || lowerQuery.contains("新建") || 
            lowerQuery.contains("添加") || lowerQuery.contains("加入") ||
            lowerQuery.contains("create") || lowerQuery.contains("add")) {
            return IntentType.BATCH_MANAGEMENT;
        }
        if (lowerQuery.contains("环境") || lowerQuery.contains("资源") || 
            lowerQuery.contains("申请") || lowerQuery.contains("回收") ||
            lowerQuery.contains("env") || lowerQuery.contains("resource")) {
            return IntentType.ENV_MANAGEMENT;
        }
        return IntentType.UNKNOWN;
    }
    
    /**
     * 按相关性排序操作记录
     */
    private java.util.List<java.util.Map<?, ?>> sortOperationsByRelevance(
            java.util.List<java.util.Map<?, ?>> ops, IntentType intentType) {
        
        return ops.stream()
            .sorted((a, b) -> {
                int scoreA = calculateRelevanceScore(a, intentType);
                int scoreB = calculateRelevanceScore(b, intentType);
                // 分数高的排前面
                return Integer.compare(scoreB, scoreA);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 计算操作与意图的相关性分数
     */
    private int calculateRelevanceScore(java.util.Map<?, ?> op, IntentType intentType) {
        String action = op.get("action") != null ? op.get("action").toString().toLowerCase() : "";
        String result = op.get("result") != null ? op.get("result").toString() : "";
        boolean isSuccess = isOperationSuccess(result);
        int score = 0;
        
        // 成功操作加分
        if (isSuccess) score += 10;
        
        // 根据意图类型匹配操作
        switch (intentType) {
            case BATCH_EXECUTION:
                // 执行批次意图：优先 createBatch（需要batchId），其次 addCasesToBatch
                if (action.contains("createbatch")) score += 20;
                else if (action.contains("addcasestobatch")) score += 15;
                else if (action.contains("executebatch")) score += 5;
                break;
            case BATCH_MANAGEMENT:
                // 批次管理意图：优先最近的相关操作
                if (action.contains("createbatch")) score += 15;
                else if (action.contains("addcasestobatch")) score += 15;
                break;
            case ENV_MANAGEMENT:
                // 环境管理意图
                if (action.contains("applyresource")) score += 15;
                else if (action.contains("recycleresource")) score += 15;
                break;
            default:
                // 未知意图：优先成功操作
                if (isSuccess) score += 5;
        }
        
        return score;
    }
    
    /**
     * 选择最佳操作记录
     */
    private java.util.Map<?, ?> selectBestOperation(
            java.util.List<java.util.Map<?, ?>> sortedOps, IntentType intentType) {
        
        if (sortedOps.isEmpty()) return null;
        
        // 优先选择成功的相关操作
        for (java.util.Map<?, ?> op : sortedOps) {
            String result = op.get("result") != null ? op.get("result").toString() : "";
            if (isOperationSuccess(result) && isRelatedOperation(op, intentType)) {
                return op;
            }
        }
        
        // 如果没有成功的相关操作，返回第一个（时间最近的）
        return sortedOps.get(0);
    }
    
    /**
     * 判断操作是否与意图相关
     */
    private boolean isRelatedOperation(java.util.Map<?, ?> op, IntentType intentType) {
        String action = op.get("action") != null ? op.get("action").toString().toLowerCase() : "";
        
        switch (intentType) {
            case BATCH_EXECUTION:
            case BATCH_MANAGEMENT:
                return action.contains("batch") || action.contains("case");
            case ENV_MANAGEMENT:
                return action.contains("resource") || action.contains("env");
            default:
                return true;
        }
    }
    
    /**
     * 判断操作是否成功
     */
    private boolean isOperationSuccess(String resultStr) {
        if (resultStr == null || resultStr.isEmpty()) return false;
        try {
            // 简单检查是否包含 "success":true
            return resultStr.contains("\"success\":true") || resultStr.contains("\"success\": true");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 从 result 中提取关键信息
     */
    private String extractKeyInfoFromResult(String action, String resultStr) {
        if (resultStr == null || resultStr.isEmpty()) return "";
        
        try {
            // 提取 batchId
            if (action.toLowerCase().contains("createbatch")) {
                int batchIdStart = resultStr.indexOf("\"batchId\":\"");
                if (batchIdStart != -1) {
                    int valueStart = batchIdStart + 11;
                    int valueEnd = resultStr.indexOf("\"", valueStart);
                    if (valueEnd != -1) {
                        return "批次号=" + resultStr.substring(valueStart, valueEnd);
                    }
                }
            }
            // 提取 executionId
            if (action.toLowerCase().contains("executebatch")) {
                int execIdStart = resultStr.indexOf("\"executionId\":\"");
                if (execIdStart != -1) {
                    int valueStart = execIdStart + 15;
                    int valueEnd = resultStr.indexOf("\"", valueStart);
                    if (valueEnd != -1) {
                        return "执行号=" + resultStr.substring(valueStart, valueEnd);
                    }
                }
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return "";
    }

    /**
     * 第二轮交互：执行确认后的任务
     * POST /api/agent/execute
     */
    @PostMapping("/execute")
    public Mono<ResponseEntity<AgentResponse>> executeTask(@RequestBody ExecuteRequest request) {
        log.info("[Controller] Execute request: {}", request.getRequestId());

        return agentService.processSecondRound(
                request.getRequestId(),
                request.getQuery(),
                request.getParameters(),
                request.getUserId(),
                request.getSessionId()
            )
            .map(ResponseEntity::ok)
            .onErrorResume(error -> {
                log.error("[Controller] Execute error: {}", error.getMessage());
                return Mono.just(ResponseEntity.ok(AgentResponse.builder()
                    .type(AgentResponse.ResponseType.ERROR)
                    .errorMessage(error.getMessage())
                    .build()));
            });
    }

    /**
     * 获取缓存的意图解析结果
     * GET /api/agent/intent/{requestId}
     */
    @GetMapping("/intent/{requestId}")
    public ResponseEntity<IntentParseResult> getIntentResult(@PathVariable String requestId) {
        IntentParseResult result = agentService.getCachedIntent(requestId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 健康检查
     * GET /api/agent/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Agent system is running");
    }

    // ============ 请求DTO ============

    @Data
    public static class IntentRequest {
        private String query;
        private String userId;
        private String sessionId;
    }

    @Data
    public static class ExecuteRequest {
        private String requestId;
        private String query;
        private String userId;
        private String sessionId;
        private Map<String, Object> parameters;
    }
}
