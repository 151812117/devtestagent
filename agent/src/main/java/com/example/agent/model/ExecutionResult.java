package com.example.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 结果消息
     */
    private String message;
    
    /**
     * 返回数据
     */
    private Map<String, Object> data;
    
    /**
     * 操作类型
     */
    private String action;
    
    /**
     * 错误信息（失败时）
     */
    private String error;
}
