package com.example.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 智能体响应
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
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntentParseResult {
        private String action;
        private String target;
        private Map<String, Object> parameters;
        private String think;
        private Map<String, String> missingParameters;
        private String confirmationMessage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionResult {
        private boolean success;
        private String message;
        private Map<String, Object> data;
    }
}
