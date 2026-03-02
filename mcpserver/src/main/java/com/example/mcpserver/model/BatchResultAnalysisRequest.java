package com.example.mcpserver.model;

import lombok.Data;

/**
 * 批次结果分析请求
 */
@Data
public class BatchResultAnalysisRequest {
    
    /**
     * 执行编号
     */
    private String executionId;
}
