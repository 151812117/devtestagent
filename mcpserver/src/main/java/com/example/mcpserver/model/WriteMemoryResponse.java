package com.example.mcpserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 写入记忆响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WriteMemoryResponse {
    
    /**
     * 记忆ID
     */
    private String memoryId;
    
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 存储类型 (SHORT_TERM/LONG_TERM)
     */
    private String storageType;
    
    /**
     * 消息
     */
    private String message;
}
