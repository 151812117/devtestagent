package com.example.mcpserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 批次结果分析响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchResultAnalysisResponse {
    
    /**
     * 成功标识
     */
    private Boolean success;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 分析结果
     */
    private AnalysisResult data;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisResult {
        /**
         * 总案例数
         */
        private Integer totalCases;
        
        /**
         * 通过数
         */
        private Integer passedCount;
        
        /**
         * 失败数
         */
        private Integer failedCount;
        
        /**
         * 通过率
         */
        private Double passRate;
        
        /**
         * 执行时长(秒)
         */
        private Double executionTime;
        
        /**
         * 失败详情
         */
        private List<Map<String, String>> failureDetails;
        
        /**
         * 性能指标
         */
        private Map<String, Object> performanceMetrics;
    }
}
