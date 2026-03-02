package com.example.mcpserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 添加案例到批次响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddCasesResponse {
    
    /**
     * 成功标识
     */
    private Boolean success;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 成功添加的案例数
     */
    private Integer addedCount;
}
