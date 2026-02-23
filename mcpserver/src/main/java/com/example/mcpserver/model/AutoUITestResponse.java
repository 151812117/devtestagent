package com.example.mcpserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 自动化界面测试响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoUITestResponse {
    
    /**
     * 测试ID
     */
    private String testId;
    
    /**
     * 测试状态
     */
    private String status;
    
    /**
     * 通过用例数
     */
    private Integer passedCount;
    
    /**
     * 失败用例数
     */
    private Integer failedCount;
    
    /**
     * 跳过的用例数
     */
    private Integer skippedCount;
    
    /**
     * 总用例数
     */
    private Integer totalCount;
    
    /**
     * 执行时间(秒)
     */
    private Double executionTime;
    
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
     * 截图列表
     */
    private List<String> screenshots;
    
    /**
     * 失败详情
     */
    private List<String> failedDetails;
}
