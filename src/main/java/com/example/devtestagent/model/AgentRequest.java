package com.example.devtestagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 智能体请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequest {
    
    /**
     * 请求ID
     */
    private String requestId;
    
    /**
     * 用户原始输入
     */
    private String userQuery;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 参数（确认后的参数）
     */
    private Map<String, Object> parameters;
    
    /**
     * 记忆内容
     */
    private String memory;
    
    /**
     * 请求阶段（INTENT_PARSE / EXECUTION）
     */
    private RequestPhase phase;
    
    public enum RequestPhase {
        INTENT_PARSE,   // 意图解析阶段
        EXECUTION       // 执行阶段
    }
}
