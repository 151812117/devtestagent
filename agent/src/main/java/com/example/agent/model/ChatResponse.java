package com.example.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Chat 接口响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    
    /**
     * 响应ID
     */
    private String responseId;
    
    /**
     * 请求ID
     */
    private String requestId;
    
    /**
     * 响应类型：INTENT_PARSE_RESULT - 意图解析结果，EXECUTION_RESULT - 执行结果，ERROR - 错误
     */
    private String type;
    
    /**
     * 意图解析结果（phase=INTENT_PARSE 时返回）
     */
    private IntentParseResult intentResult;
    
    /**
     * 执行结果（phase=EXECUTION 时返回）
     */
    private ExecutionResult executionResult;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 执行结果内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionResult {
        private boolean success;
        private String message;
        private Map<String, Object> data;
    }
    
    /**
     * 意图解析结果内部类
     */
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
}
