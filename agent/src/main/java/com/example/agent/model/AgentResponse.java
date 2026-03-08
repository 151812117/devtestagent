package com.example.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 智能体响应
 * 用于子智能体返回执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {
    
    /**
     * 响应ID
     */
    private String responseId;
    
    /**
     * 请求ID
     */
    private String requestId;
    
    /**
     * 响应类型
     */
    private ResponseType type;
    
    /**
     * 意图解析结果
     */
    private IntentParseResult intentResult;
    
    /**
     * 执行结果
     */
    private ExecutionResult executionResult;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    public enum ResponseType {
        INTENT_PARSE_RESULT,
        EXECUTION_RESULT,
        ERROR
    }
    
    /**
     * 执行结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionResult {
        private boolean success;
        private String message;
        private Map<String, Object> data;
        private String action;
        private String error;
    }
}
