package com.example.mcpserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 测试结果分析响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultAnalysisResponse {
    
    /**
     * 分析ID
     */
    private String analysisId;
    
    /**
     * 测试ID
     */
    private String testId;
    
    /**
     * 分析状态
     */
    private String status;
    
    /**
     * 总结
     */
    private String summary;
    
    /**
     * 关键指标
     */
    private Map<String, Object> keyMetrics;
    
    /**
     * 发现的issues
     */
    private List<String> issues;
    
    /**
     * 优化建议
     */
    private List<String> recommendations;
    
    /**
     * 性能瓶颈
     */
    private List<String> bottlenecks;
    
    /**
     * 覆盖率分析
     */
    private Map<String, Double> coverage;
    
    /**
     * 报告URL
     */
    private String reportUrl;
    
    /**
     * 分析时间
     */
    private String analysisTime;
}
