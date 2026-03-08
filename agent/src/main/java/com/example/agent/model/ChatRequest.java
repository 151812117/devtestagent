package com.example.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Chat 接口请求参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 请求ID
     */
    private String requestId;
    
    /**
     * 用户输入内容（已组装好提示词）
     */
    private String content;
    
    /**
     * 阶段：INTENT_PARSE - 意图解析，EXECUTION - 执行
     */
    private String phase;
    
    /**
     * 上下文参数（执行阶段使用）
     */
    private Map<String, Object> context;
    
    /**
     * 原始查询（可选）
     */
    private String originalQuery;
    
    /**
     * 记忆内容（可选，用于子Agent参考）
     */
    private String memoryContent;
}
