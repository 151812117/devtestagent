package com.example.mcpserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 执行批次响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteBatchResponse {
    
    /**
     * 成功标识
     */
    private Boolean success;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 执行编号
     */
    private String executionId;
}
