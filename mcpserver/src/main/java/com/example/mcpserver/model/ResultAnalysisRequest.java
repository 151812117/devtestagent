package com.example.mcpserver.model;

import lombok.Data;
import java.util.List;

/**
 * 测试结果分析请求
 */
@Data
public class ResultAnalysisRequest {
    
    /**
     * 测试ID
     */
    private String testId;
    
    /**
     * 结果类型（summary/detailed/performance）
     */
    private String resultType;
    
    /**
     * 分析指标列表
     */
    private List<String> metrics;
    
    /**
     * 对比历史结果
     */
    private String compareWith;
    
    /**
     * 是否生成报告
     */
    private Boolean generateReport;
}
