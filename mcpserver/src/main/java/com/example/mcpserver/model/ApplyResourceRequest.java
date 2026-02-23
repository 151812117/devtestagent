package com.example.mcpserver.model;

import lombok.Data;

/**
 * 申请环境资源请求
 */
@Data
public class ApplyResourceRequest {
    
    /**
     * 环境类型（development/testing/staging/production）
     */
    private String envType;
    
    /**
     * 使用时长（小时）
     */
    private Integer duration;
    
    /**
     * CPU核心数
     */
    private Integer cpu;
    
    /**
     * 内存大小（GB）
     */
    private Integer memory;
    
    /**
     * 存储大小（GB）
     */
    private Integer storage;
    
    /**
     * 实例数量
     */
    private Integer instanceCount;
    
    /**
     * 使用目的
     */
    private String purpose;
    
    /**
     * 申请人
     */
    private String owner;
}
