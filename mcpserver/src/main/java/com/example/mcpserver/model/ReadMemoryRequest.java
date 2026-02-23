package com.example.mcpserver.model;

import lombok.Data;

/**
 * 读取记忆请求
 */
@Data
public class ReadMemoryRequest {
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 查询条数限制，默认10
     */
    private Integer limit;
    
    /**
     * 记忆类型过滤
     */
    private String memoryType;
    
    /**
     * 分类过滤
     */
    private String category;
    
    /**
     * 是否包含长期记忆
     */
    private Boolean includeLongTerm;
}
