package com.example.mcpserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 自动化接口测试响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoInterfaceTestResponse {
    
    /**
     * 测试ID
     */
    private String testId;
    
    /**
     * 测试状态
     */
    private String status;
    
    /**
     * 总请求数
     */
    private Integer totalRequests;
    
    /**
     * 成功数
     */
    private Integer successCount;
    
    /**
     * 失败数
     */
    private Integer failedCount;
    
    /**
     * 平均响应时间(ms)
     */
    private Double avgResponseTime;
    
    /**
     * 测试开始时间
     */
    private String startTime;
    
    /**
     * 测试结束时间
     */
    private String endTime;
    
    /**
     * 测试报告URL
     */
    private String reportUrl;
    
    /**
     * 失败详情
     */
    private List<String> failedDetails;
}
