package com.example.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 意图解析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentParseResult {
    
    /**
     * 操作类型
     */
    private String action;
    
    /**
     * 操作目标
     */
    private String target;
    
    /**
     * 提取的参数
     */
    private Map<String, Object> parameters;
    
    /**
     * 思考过程
     */
    private String think;
    
    /**
     * 缺失的参数
     */
    private Map<String, String> missingParameters;
    
    /**
     * 置信度
     */
    private double confidence;
    
    /**
     * 原始查询
     */
    private String originalQuery;
    
    /**
     * 是否需要确认
     */
    private boolean needConfirmation;
    
    /**
     * 确认消息
     */
    private String confirmationMessage;
    
    /**
     * 参数说明
     */
    private Map<String, String> parameterDescriptions;
}
