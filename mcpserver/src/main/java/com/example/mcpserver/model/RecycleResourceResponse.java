package com.example.mcpserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回收环境资源响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecycleResourceResponse {
    
    /**
     * 回收ID
     */
    private String recycleId;
    
    /**
     * 环境ID
     */
    private String envId;
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 回收时间
     */
    private String recycleTime;
    
    /**
     * 释放资源列表
     */
    private String releasedResources;
}
