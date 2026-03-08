package com.example.devtestagent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 记忆上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryContext {
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 历史任务
     */
    private List<MemoryEntry> historyTasks;
    
    /**
     * 最近环境操作记录
     */
    private List<MemoryEntry> recentEnvOperations;
    
    /**
     * 最近测试记录
     */
    private List<MemoryEntry> recentTestRecords;
    
    /**
     * 用户偏好
     */
    private UserPreference userPreference;
    
    /**
     * 原始数据（MCP Server 返回的 data 字段）
     */
    private Map<String, Object> data;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryEntry {
        private String id;
        private String action;
        private String target;
        private String result;
        private LocalDateTime timestamp;
        private String details;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserPreference {
        private String defaultEnvType;
        private List<String> favoriteTestScenarios;
        private String preferredNotificationWay;
    }
}
