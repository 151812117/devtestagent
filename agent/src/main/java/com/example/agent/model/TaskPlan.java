package com.example.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 任务规划
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
     * 目标智能体
     */
    private String targetAgent;
    
    /**
     * 任务描述
     */
    private String description;
    
    /**
     * 是否需要确认
     */
    private boolean needConfirmation;
    
    /**
     * 子任务列表
     */
    private List<SubTask> subTasks;
    
    public enum TaskType {
        ENV_MANAGEMENT,
        TEST_EXECUTION,
        UNKNOWN
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubTask {
        private String step;
        private String description;
        private String targetAgent;
    }
}
