package com.example.devtestagent.service;

import com.example.devtestagent.agent.master.MasterAgent;
import com.example.devtestagent.model.AgentResponse;
import com.example.devtestagent.model.IntentParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体服务
 * 封装多轮交互的业务逻辑
 */
@Slf4j
@Service
public class AgentService {

    private final MasterAgent masterAgent;
    
    // 临时存储意图解析结果，用于第二轮确认
    private final Map<String, IntentParseResult> intentCache = new ConcurrentHashMap<>();

    public AgentService(MasterAgent masterAgent) {
        this.masterAgent = masterAgent;
    }

    /**
     * 第一轮：处理用户输入，进行意图解析
     */
    public Mono<AgentResponse> processFirstRound(String userQuery, String userId, String sessionId) {
        log.info("[AgentService] First round - userQuery: {}, userId: {}", userQuery, userId);

        return masterAgent.processIntentParse(userQuery, userId, sessionId)
            .doOnNext(response -> {
                // 缓存意图解析结果
                if (response.getIntentResult() != null) {
                    intentCache.put(response.getRequestId(), response.getIntentResult());
                    log.info("[AgentService] Cached intent result for request: {}", response.getRequestId());
                }
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
        IntentParseResult originalIntent = intentCache.get(requestId);
        if (originalIntent == null) {
            return Mono.error(new IllegalStateException("Intent parse result not found for request: " + requestId));
        }

        // 使用用户确认后的参数，并加入 action（确保 action 没有空格）
        java.util.Map<String, Object> paramsWithAction = new java.util.HashMap<>(confirmedParameters);
        String action = originalIntent.getAction() != null ? originalIntent.getAction().trim() : "";
        paramsWithAction.put("action", action);
        originalIntent.setParameters(paramsWithAction);
        
        log.info("[AgentService] Executing with action: '{}'", action);

        // 创建确认参数的适配器
        MasterAgent.IntentParseIntent confirmedIntent = new MasterAgent.IntentParseIntent() {
            @Override
            public String getAction() {
                return originalIntent.getAction();
            }

            @Override
            public Map<String, Object> getParameters() {
                return paramsWithAction;
            }
        };

        return masterAgent.processExecution(requestId, userQuery, confirmedIntent, userId, sessionId)
            .doOnNext(response -> {
                // 清除缓存
                intentCache.remove(requestId);
                log.info("[AgentService] Execution completed for request: {}", requestId);
            });
    }

    /**
     * 生成会话ID
     */
    public String generateSessionId() {
        return "session_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 获取缓存的意图结果（用于前端展示）
     */
    public IntentParseResult getCachedIntent(String requestId) {
        return intentCache.get(requestId);
    }
}
