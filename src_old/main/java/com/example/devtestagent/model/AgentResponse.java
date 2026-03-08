package com.example.devtestagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
     * 意图解析结果（第一阶段）
     */
    private IntentParseResult intentResult;
    
    /**
     * 执行结果（第二阶段）
     */
    private ExecutionResult executionResult;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    public enum ResponseType {
        INTENT_PARSE_RESULT,    // 意图解析结果
        EXECUTION_RESULT,       // 执行结果
        ERROR                   // 错误
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionResult {
        private boolean success;
        private String message;
        private Object data;
        private String taskId;
    }
}
