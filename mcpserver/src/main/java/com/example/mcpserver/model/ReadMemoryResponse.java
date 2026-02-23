package com.example.mcpserver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 读取记忆响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadMemoryResponse {
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 短期记忆列表
     */
    private List<MemoryEntry> shortTermMemories;
    
    /**
     * 长期记忆概要
     */
    private Map<String, Object> longTermProfile;
    
    /**
     * 会话上下文
     */
    private SessionContext sessionContext;
    
    /**
     * 总计条数
     */
    private Integer totalCount;
    
    /**
     * 数据
     */
    private Map<String, Object> data;
    
    /**
     * 会话上下文内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionContext {
        /**
         * 当前任务类型
         */
        private String currentTask;
        
        /**
         * 是否有待确认操作
         */
        private Boolean pendingConfirmation;
        
         /**
          * 最后活动时间
          */
         private String lastActivity;
         
         /**
          * 当前环境
          */
         private String currentEnv;
         
         /**
          * 当前测试
          */
         private String currentTest;
    }
}
