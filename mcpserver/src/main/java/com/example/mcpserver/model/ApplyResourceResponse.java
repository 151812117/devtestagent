package com.example.mcpserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 申请环境资源响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyResourceResponse {
    
    /**
     * 环境ID
     */
    private String envId;
    
    /**
     * 环境名称
     */
    private String envName;
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 创建时间
     */
    private String createTime;
    
    /**
     * 过期时间
     */
    private String expireTime;
    
    /**
     * 访问地址
     */
    private String accessUrl;
}
