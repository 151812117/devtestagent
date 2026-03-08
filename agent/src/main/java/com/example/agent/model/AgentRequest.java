package com.example.agent.model;

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
     * 用户查询
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
     * 请求阶段
     */
    private RequestPhase phase;
    
    /**
     * 记忆内容
     */
    private String memory;
    
    /**
     * 参数（执行阶段使用）
     */
    private Map<String, Object> parameters;
    
    public enum RequestPhase {
        INTENT_PARSE,
        EXECUTION
    }
}
