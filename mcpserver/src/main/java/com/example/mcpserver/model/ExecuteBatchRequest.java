package com.example.mcpserver.model;

import lombok.Data;

/**
 * 执行批次请求
 */
@Data
public class ExecuteBatchRequest {
    
    /**
     * 批次编号
     */
    private String batchId;
    
    /**
     * 系统编号
     */
    private String systemCode;
}
