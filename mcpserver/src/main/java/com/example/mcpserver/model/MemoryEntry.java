package com.example.mcpserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 记忆条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryEntry {
    
    /**
     * 唯一标识
     */
    private String id;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 时间戳
     */
    private String timestamp;
    
    /**
     * 记忆类型 (EPHEMERAL/SHORT_TERM/LONG_TERM/SEMANTIC)
     */
    private String memoryType;
    
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
     * 重要程度 (1-10)
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
     * 过期时间
     */
    private String expirationTime;
    
    /**
     * 访问次数
     */
    @Builder.Default
    private Integer accessCount = 0;
    
    /**
     * 最后访问时间
     */
    private String lastAccessTime;
}
