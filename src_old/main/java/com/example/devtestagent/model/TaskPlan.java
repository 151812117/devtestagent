package com.example.devtestagent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 任务规划结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskPlan {
    
    /**
     * 任务类型
     */
    private TaskType taskType;
    
    /**
     * 目标子智能体
     */
    private String targetAgent;
    
    /**
     * 任务描述
     */
    private String description;
    
    /**
     * 子任务列表
     */
    private List<SubTask> subTasks;
    
    /**
     * 是否需要确认
     */
    private boolean needConfirmation;
    
    public enum TaskType {
        ENV_MANAGEMENT,      // 环境管理
        TEST_EXECUTION,      // 测试执行
        UNKNOWN              // 未知
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTask {
        private String id;
        private String description;
        private String toolName;
        private String status;
    }
}
