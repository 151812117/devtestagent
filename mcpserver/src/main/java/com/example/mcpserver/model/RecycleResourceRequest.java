package com.example.mcpserver.model;

import lombok.Data;

/**
 * 回收环境资源请求
 */
@Data
public class RecycleResourceRequest {
    
    /**
     * 环境ID
     */
    private String envId;
    
    /**
     * 资源ID
     */
    private String resourceId;
    
    /**
     * 回收类型（full/partial）
     */
    private String recycleType;
    
    /**
     * 回收原因
     */
    private String reason;
}
