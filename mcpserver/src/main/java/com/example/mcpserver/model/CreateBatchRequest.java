package com.example.mcpserver.model;

import lombok.Data;

/**
 * 创建测试批次请求
 */
@Data
public class CreateBatchRequest {
    
    /**
     * 被测系统名
     */
    private String systemName;
    
    /**
     * 被测系统编号
     */
    private String systemCode;
    
    /**
     * 批次标识
     */
    private String batchLabel;
}
