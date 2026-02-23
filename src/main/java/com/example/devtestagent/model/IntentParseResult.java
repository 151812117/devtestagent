package com.example.devtestagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 意图解析结果（JSON格式）
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
     * 操作对象类型
     */
    private String target;
    
    /**
     * 解析出的参数
     */
    private Map<String, Object> parameters;
    
    /**
     * 参数说明
     */
    private Map<String, String> parameterDescriptions;
    
    /**
     * 置信度
     */
    private double confidence;
    
    /**
     * 原始用户输入
     */
    private String originalQuery;
    
    /**
     * 是否需要确认
     */
    private boolean needConfirmation;
    
    /**
     * 确认提示信息
     */
    private String confirmationMessage;
}
