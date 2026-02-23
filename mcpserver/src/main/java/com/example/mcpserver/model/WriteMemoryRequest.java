package com.example.mcpserver.model;

import lombok.Data;

import java.util.List;

/**
 * 写入记忆请求
 */
@Data
public class WriteMemoryRequest {
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 操作类型
     */
    private String action;
    
    /**
     * 操作对象
     */
    private String target;
    
    /**
     * 操作结果
     */
    private String result;
    
    /**
     * 详细内容
     */
    private String details;
    
    /**
     * 时间戳
     */
    private String timestamp;
    
    /**
     * 重要程度 (1-10)，默认为5
     */
    private Integer importance;
    
    /**
     * 分类标签
     */
    private String category;
    
    /**
     * 关键词列表
     */
    private List<String> keywords;
    
    /**
     * 是否转为长期记忆
     */
    private Boolean persistLongTerm;
}
