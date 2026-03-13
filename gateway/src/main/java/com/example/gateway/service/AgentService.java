package com.example.gateway.service;

import com.example.gateway.client.AgentClient;
import com.example.gateway.client.MemoryClient;
import com.example.gateway.model.AgentResponse;
import com.example.gateway.model.MemoryContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 智能体服务
 * 封装多轮交互的业务逻辑，组装提示词并调用 Agent
 */
@Slf4j
@Service
public class AgentService {

    private final AgentClient agentClient;
    private final MemoryClient memoryClient;
    
    // 临时存储意图解析结果，用于第二轮确认
    private final Map<String, AgentResponse.IntentParseResult> intentCache = new ConcurrentHashMap<>();

    public AgentService(AgentClient agentClient, MemoryClient memoryClient) {
        this.agentClient = agentClient;
        this.memoryClient = memoryClient;
    }

    /**
     * 第一轮：处理用户输入，进行意图解析
     * 1. 读取记忆
     * 2. 组装提示词（记忆+用户输入）
     * 3. 调用 Agent 的 chat 接口
     * 4. 如果是菜单推荐类型，直接执行并返回结果（不需要参数确认）
     */
    public Mono<AgentResponse> processFirstRound(String userQuery, String userId, String sessionId) {
        log.info("[AgentService] First round - userQuery: {}, userId: {}", userQuery, userId);
        
        String requestId = generateRequestId();

        // 1. 读取用户记忆
        return memoryClient.readMemory(userId, sessionId)
            .flatMap(memory -> {
                // 2. 组装带记忆上下文的提示词
                String memoryContent = formatMemoryContent(userQuery, memory);
                String queryWithMemory = buildQueryWithMemory(userQuery, memoryContent);
                
                log.info("[AgentService] Query with memory: {}", queryWithMemory);
                
                // 3. 调用 Agent 的 chat 接口
                return agentClient.chatIntentParse(userId, sessionId, requestId, queryWithMemory, memoryContent);
            })
            .flatMap(response -> {
                // 4. 检查是否是菜单推荐类型
                if (response.getIntentResult() != null && isMenuRecommendation(response.getIntentResult())) {
                    log.info("[AgentService] Menu recommendation detected, executing directly without confirmation");
                    // 直接执行，不需要参数确认
                    return executeMenuRecommendation(response, userId, sessionId, requestId, userQuery);
                }
                // 缓存意图解析结果（非菜单推荐类型需要第二轮确认）
                if (response.getIntentResult() != null) {
                    intentCache.put(requestId, response.getIntentResult());
                    log.info("[AgentService] Cached intent result for request: {}", requestId);
                }
                return Mono.just(response);
            });
    }
    
    /**
     * 判断是否是菜单推荐类型
     */
    private boolean isMenuRecommendation(AgentResponse.IntentParseResult intentResult) {
        if (intentResult == null || intentResult.getAction() == null) {
            return false;
        }
        String action = intentResult.getAction().toLowerCase();
        return action.startsWith("recommendmenu") || action.startsWith("menurecommendation");
    }
    
    /**
     * 直接执行菜单推荐
     */
    private Mono<AgentResponse> executeMenuRecommendation(AgentResponse intentResponse, 
                                                           String userId, String sessionId, 
                                                           String requestId, String userQuery) {
        AgentResponse.IntentParseResult intentResult = intentResponse.getIntentResult();
        String action = intentResult.getAction();
        Map<String, Object> parameters = intentResult.getParameters() != null ? 
            intentResult.getParameters() : new java.util.HashMap<>();
        
        // 调用 Agent 执行
        return agentClient.chatExecution(userId, sessionId, requestId, userQuery, action, parameters)
            .map(execResponse -> {
                // 构建最终响应 - 将执行结果包装成直接回答的形式
                AgentResponse finalResponse = new AgentResponse();
                finalResponse.setRequestId(requestId);
                finalResponse.setType(AgentResponse.ResponseType.EXECUTION_RESULT);
                finalResponse.setIntentResult(intentResult);
                finalResponse.setExecutionResult(execResponse.getExecutionResult());
                return finalResponse;
            });
    }

    /**
     * 第二轮：执行确认后的任务
     */
    public Mono<AgentResponse> processSecondRound(String requestId, String userQuery,
                                                   Map<String, Object> confirmedParameters,
                                                   String userId, String sessionId) {
        log.info("[AgentService] Second round - requestId: {}, userId: {}", requestId, userId);

        // 从缓存获取原始意图解析结果
        AgentResponse.IntentParseResult originalIntent = intentCache.get(requestId);
        if (originalIntent == null) {
            return Mono.error(new IllegalStateException("Intent parse result not found for request: " + requestId));
        }

        // 使用用户确认后的参数，并加入 action
        Map<String, Object> paramsWithAction = new java.util.HashMap<>();
        if (confirmedParameters != null) {
            paramsWithAction.putAll(confirmedParameters);
        }
        String action = originalIntent.getAction() != null ? originalIntent.getAction().trim() : "";
        paramsWithAction.put("action", action);
        
        log.info("[AgentService] Executing with action: '{}'", action);

        // 调用 Agent 的 chat 接口执行
        return agentClient.chatExecution(userId, sessionId, requestId, userQuery, action, paramsWithAction)
            .doOnNext(response -> {
                // 清除缓存
                intentCache.remove(requestId);
                log.info("[AgentService] Execution completed for request: {}", requestId);
                
                // 写入记忆
                if (response.getExecutionResult() != null) {
                    recordExecutionResult(userId, sessionId, action, confirmedParameters, response.getExecutionResult());
                }
            });
    }

    /**
     * 构建带记忆上下文的查询
     */
    private String buildQueryWithMemory(String originalQuery, String memoryContent) {
        if (memoryContent == null || memoryContent.isEmpty()) {
            return originalQuery;
        }
        return "用户操作历史：" + memoryContent + "\n\n当前需求：" + originalQuery;
    }

    /**
     * 格式化记忆内容
     * 智能选择最相关的操作记录，优先显示成功且与当前意图相关的操作
     */
    private String formatMemoryContent(String userQuery, MemoryContext memory) {
        if (memory == null || memory.getData() == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        String lowerQuery = userQuery.toLowerCase();
        
        // 判断用户意图类型
        IntentType intentType = detectIntentType(lowerQuery);
        
        // 添加近期操作记录
        if (memory.getData().get("recentOperations") != null) {
            java.util.List<?> operations = (java.util.List<?>) memory.getData().get("recentOperations");
            if (!operations.isEmpty()) {
                // 过滤并转换为Map列表
                java.util.List<java.util.Map<?, ?>> validOps = operations.stream()
                    .filter(op -> op instanceof java.util.Map)
                    .map(op -> (java.util.Map<?, ?>) op)
                    .filter(map -> map.get("action") != null)
                    .sorted((a, b) -> {
                        // 按时间倒序排列
                        String timeA = a.get("timestamp") != null ? a.get("timestamp").toString() : "";
                        String timeB = b.get("timestamp") != null ? b.get("timestamp").toString() : "";
                        return timeB.compareTo(timeA);
                    })
                    .collect(Collectors.toList());
                
                if (!validOps.isEmpty()) {
                    // 选择最相关的一条
                    java.util.Map<?, ?> bestOp = selectBestOperation(validOps, intentType);
                    
                    if (bestOp != null) {
                        String action = bestOp.get("action") != null ? bestOp.get("action").toString() : "";
                        String target = bestOp.get("target") != null ? bestOp.get("target").toString() : "";
                        
                        // 获取 result 对象（可能是 Map 或 String）
                        Object resultObj = bestOp.get("result");
                        java.util.Map<?, ?> resultMap = parseResultToMap(resultObj);
                        
                        // 提取关键信息
                        String extractedInfo = extractKeyInfoFromResult(action, resultMap);
                        boolean isSuccess = isOperationSuccess(resultMap);
                        
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
                    }
                }
            }
        }
        
        return sb.toString();
    }

    private enum IntentType {
        BATCH_EXECUTION,
        BATCH_MANAGEMENT,
        ENV_MANAGEMENT,
        UNKNOWN
    }

    private IntentType detectIntentType(String lowerQuery) {
        if (lowerQuery.contains("执行") || lowerQuery.contains("运行") || 
            lowerQuery.contains("开始") || lowerQuery.contains("启动")) {
            return IntentType.BATCH_EXECUTION;
        }
        if (lowerQuery.contains("创建") || lowerQuery.contains("新建") || 
            lowerQuery.contains("添加") || lowerQuery.contains("加入")) {
            return IntentType.BATCH_MANAGEMENT;
        }
        if (lowerQuery.contains("环境") || lowerQuery.contains("资源") || 
            lowerQuery.contains("申请") || lowerQuery.contains("回收")) {
            return IntentType.ENV_MANAGEMENT;
        }
        return IntentType.UNKNOWN;
    }

    private java.util.Map<?, ?> selectBestOperation(java.util.List<java.util.Map<?, ?>> ops, IntentType intentType) {
        if (ops.isEmpty()) return null;
        
        // 优先选择成功的相关操作
        for (java.util.Map<?, ?> op : ops) {
            Object resultObj = op.get("result");
            java.util.Map<?, ?> resultMap = parseResultToMap(resultObj);
            if (isOperationSuccess(resultMap) && isRelatedOperation(op, intentType)) {
                return op;
            }
        }
        
        // 如果没有成功的相关操作，返回第一个（时间最近的）
        return ops.get(0);
    }
    
    /**
     * 将 result 对象解析为 Map
     * 支持 Map 类型或 Java Map.toString() 格式的字符串
     */
    private java.util.Map<?, ?> parseResultToMap(Object resultObj) {
        if (resultObj == null) {
            return null;
        }
        
        // 如果已经是 Map，直接返回
        if (resultObj instanceof java.util.Map) {
            return (java.util.Map<?, ?>) resultObj;
        }
        
        // 如果是字符串，尝试解析 Java Map.toString() 格式
        if (resultObj instanceof String) {
            String str = ((String) resultObj).trim();
            // 检查是否是 Map.toString() 格式: {key=value, key2=value2}
            if (str.startsWith("{") && str.endsWith("}")) {
                return parseJavaMapString(str);
            }
        }
        
        return null;
    }
    
    /**
     * 解析 Java Map.toString() 格式的字符串为 Map
     * 格式: {key1=value1, key2={nestedKey=nestedValue}, key3=value3}
     */
    private java.util.Map<String, Object> parseJavaMapString(String mapString) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        
        // 去掉首尾的 {}
        String content = mapString.substring(1, mapString.length() - 1).trim();
        if (content.isEmpty()) {
            return result;
        }
        
        // 解析键值对，需要处理嵌套的 {}
        int depth = 0;
        int start = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                // 找到一个顶级的逗号，分割键值对
                String pair = content.substring(start, i).trim();
                parseKeyValuePair(pair, result);
                start = i + 1;
            }
        }
        // 处理最后一个键值对
        String lastPair = content.substring(start).trim();
        parseKeyValuePair(lastPair, result);
        
        return result;
    }
    
    /**
     * 解析单个键值对
     */
    private void parseKeyValuePair(String pair, java.util.Map<String, Object> map) {
        int eqIndex = pair.indexOf('=');
        if (eqIndex > 0) {
            String key = pair.substring(0, eqIndex).trim();
            String value = pair.substring(eqIndex + 1).trim();
            
            // 处理嵌套的 Map
            if (value.startsWith("{") && value.endsWith("}")) {
                map.put(key, parseJavaMapString(value));
            } else {
                map.put(key, value);
            }
        }
    }

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

    private boolean isOperationSuccess(java.util.Map<?, ?> resultMap) {
        if (resultMap == null) return false;
        Object success = resultMap.get("success");
        if (success instanceof Boolean) {
            return (Boolean) success;
        }
        return "true".equals(String.valueOf(success));
    }

    private String extractKeyInfoFromResult(String action, java.util.Map<?, ?> resultMap) {
        if (resultMap == null) return "";
        
        try {
            // 获取 data 对象
            Object dataObj = resultMap.get("data");
            if (dataObj instanceof java.util.Map) {
                java.util.Map<?, ?> dataMap = (java.util.Map<?, ?>) dataObj;
                
                // 提取 batchId
                if (action.toLowerCase().contains("createbatch")) {
                    Object batchId = dataMap.get("batchId");
                    if (batchId != null) {
                        return "批次号=" + batchId.toString();
                    }
                }
                
                // 提取 executionId
                if (action.toLowerCase().contains("executebatch")) {
                    Object executionId = dataMap.get("executionId");
                    if (executionId != null) {
                        return "执行号=" + executionId.toString();
                    }
                }
                
                // 提取 envId
                if (action.toLowerCase().contains("applyresource")) {
                    Object envId = dataMap.get("envId");
                    if (envId != null) {
                        return "环境ID=" + envId.toString();
                    }
                }
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return "";
    }

    /**
     * 生成会话ID
     */
    public String generateSessionId() {
        return "session_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 生成请求ID
     */
    public String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 获取缓存的意图结果
     */
    public AgentResponse.IntentParseResult getCachedIntent(String requestId) {
        return intentCache.get(requestId);
    }

    /**
     * 记录执行结果到记忆
     */
    private void recordExecutionResult(String userId, String sessionId, String action,
                                        Map<String, Object> parameters, AgentResponse.ExecutionResult result) {
        try {
            String resultStr = result.isSuccess() ? "success" : "failed";
            String resultData = result.getData() != null ? result.getData().toString() : "";
            
            // 判断操作类型，写入相应记忆
            if (action.startsWith("applyResource") || action.startsWith("recycleResource")) {
                String envType = parameters.getOrDefault("envType", "unknown").toString();
                memoryClient.recordEnvOperation(userId, sessionId, action, envType, resultData);
                log.info("[AgentService] Recorded env operation to memory: {}", action);
            } else if (action.startsWith("auto") || action.startsWith("resultAnalysis") ||
                       action.startsWith("createBatch") || action.startsWith("addCasesToBatch") ||
                       action.startsWith("executeBatch") || action.startsWith("analyzeBatchResult")) {
                String scenario = parameters.getOrDefault("testScenario", 
                    parameters.getOrDefault("systemName", "unknown")).toString();
                memoryClient.recordTestOperation(userId, sessionId, action, scenario, resultData);
                log.info("[AgentService] Recorded test operation to memory: {}", action);
            }
        } catch (Exception e) {
            log.error("[AgentService] Failed to record execution result: {}", e.getMessage());
        }
    }
}
