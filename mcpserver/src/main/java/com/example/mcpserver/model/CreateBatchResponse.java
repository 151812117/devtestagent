package com.example.mcpserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建测试批次响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBatchResponse {
    
    /**
     * 成功标识
     */
    private Boolean success;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 批次编号
     */
    private String batchId;
}
