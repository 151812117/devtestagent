package com.example.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    
    private String userId;
    private String sessionId;
    private List<MemoryEntry> historyTasks;
    private List<MemoryEntry> recentEnvOperations;
    private List<MemoryEntry> recentTestRecords;
    private UserPreference userPreference;
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
        private String timestamp;
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
