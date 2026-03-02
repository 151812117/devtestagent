package com.example.mcpserver.service;

import com.example.mcpserver.mcp.McpServerEndpoint;
import com.example.mcpserver.mcp.ToolMapping;
import com.example.mcpserver.model.*;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 测试工具 MCP Server
 * 提供测试批次管理相关工具
 */
@Slf4j
@Component
@McpServerEndpoint(path = "/mcp", name = "test-tools", version = "1.0.0")
public class TestTools {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // ==================== 测试批次管理工具 ====================
    
    /**
     * 创建测试批次
     */
    @ToolMapping(
        name = "createBatch",
        description = "创建一个新的测试批次，用于组织和管理测试案例",
        inputType = CreateBatchRequest.class
    )
    public CreateBatchResponse createBatch(CreateBatchRequest request) {
        log.info("[MCP] createBatch called: systemName={}, systemCode={}", 
            request.getSystemName(), request.getSystemCode());
        
        String batchId = generateBatchId();
        
        CreateBatchResponse response = CreateBatchResponse.builder()
            .success(true)
            .message("测试批次创建成功")
            .batchId(batchId)
            .build();
        
        log.info("[MCP] Batch created: batchId={}", batchId);
        return response;
    }
    
    /**
     * 添加案例到批次
     */
    @ToolMapping(
        name = "addCasesToBatch",
        description = "将测试案例添加到指定的测试批次中",
        inputType = AddCasesRequest.class
    )
    public AddCasesResponse addCasesToBatch(AddCasesRequest request) {
        log.info("[MCP] addCasesToBatch called: batchId={}, caseCount={}", 
            request.getBatchId(), request.getCaseIds() != null ? request.getCaseIds().size() : 0);
        
        int addedCount = request.getCaseIds() != null ? request.getCaseIds().size() : 0;
        
        AddCasesResponse response = AddCasesResponse.builder()
            .success(true)
            .message("成功添加 " + addedCount + " 个测试案例到批次")
            .addedCount(addedCount)
            .build();
        
        log.info("[MCP] Cases added to batch: batchId={}, count={}", request.getBatchId(), addedCount);
        return response;
    }
    
    /**
     * 执行批次
     */
    @ToolMapping(
        name = "executeBatch",
        description = "执行指定批次的所有测试案例",
        inputType = ExecuteBatchRequest.class
    )
    public ExecuteBatchResponse executeBatch(ExecuteBatchRequest request) {
        log.info("[MCP] executeBatch called: batchId={}, systemCode={}", 
            request.getBatchId(), request.getSystemCode());
        
        String executionId = generateExecutionId();
        
        ExecuteBatchResponse response = ExecuteBatchResponse.builder()
            .success(true)
            .message("批次执行成功")
            .executionId(executionId)
            .build();
        
        log.info("[MCP] Batch executed: batchId={}, executionId={}", request.getBatchId(), executionId);
        return response;
    }
    
    /**
     * 批次结果分析
     */
    @ToolMapping(
        name = "analyzeBatchResult",
        description = "分析批次的执行结果，生成测试报告和统计信息",
        inputType = BatchResultAnalysisRequest.class
    )
    public BatchResultAnalysisResponse analyzeBatchResult(BatchResultAnalysisRequest request) {
        log.info("[MCP] analyzeBatchResult called: executionId={}", request.getExecutionId());
        
        // 模拟分析结果
        BatchResultAnalysisResponse.AnalysisResult analysisResult = 
            BatchResultAnalysisResponse.AnalysisResult.builder()
                .totalCases(100)
                .passedCount(85)
                .failedCount(15)
                .passRate(85.0)
                .executionTime(120.5)
                .failureDetails(createMockFailureDetails())
                .performanceMetrics(createMockPerformanceMetrics())
                .build();
        
        BatchResultAnalysisResponse response = BatchResultAnalysisResponse.builder()
            .success(true)
            .message("批次结果分析完成")
            .data(analysisResult)
            .build();
        
        log.info("[MCP] Batch result analyzed: executionId={}", request.getExecutionId());
        return response;
    }
    
    // ==================== 环境管理工具（保留原有功能） ====================
    
    /**
     * 申请环境资源
     */
    @ToolMapping(
        name = "applyResource",
        description = "申请环境资源用于测试",
        inputType = ApplyResourceRequest.class
    )
    public ApplyResourceResponse applyResource(ApplyResourceRequest request) {
        log.info("[MCP] applyResource called");
        
        String envId = "env-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        
        ApplyResourceResponse response = ApplyResourceResponse.builder()
            .envId(envId)
            .envName(request.getEnvType() + "-env")
            .status("RUNNING")
            .createTime(LocalDateTime.now().format(FORMATTER))
            .expireTime(LocalDateTime.now().plusHours(request.getDuration() != null ? request.getDuration() : 24).format(FORMATTER))
            .accessUrl("https://" + envId + ".example.com")
            .build();
        
        return response;
    }
    
    /**
     * 回收环境资源
     */
    @ToolMapping(
        name = "recycleResource",
        description = "回收环境资源",
        inputType = RecycleResourceRequest.class
    )
    public RecycleResourceResponse recycleResource(RecycleResourceRequest request) {
        log.info("[MCP] recycleResource called");
        
        return RecycleResourceResponse.builder()
            .recycleId("rec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
            .envId(request.getEnvId())
            .status("RECYCLED")
            .recycleTime(LocalDateTime.now().format(FORMATTER))
            .releasedResources("CPU, Memory, Storage")
            .build();
    }
    
    // ==================== 辅助方法 ====================
    
    private String generateBatchId() {
        return "BATCH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
    
    private String generateExecutionId() {
        return "EXEC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
    
    private List<Map<String, String>> createMockFailureDetails() {
        List<Map<String, String>> failures = new ArrayList<>();
        
        Map<String, String> failure1 = new HashMap<>();
        failure1.put("caseId", "CASE-001");
        failure1.put("caseName", "用户登录测试");
        failure1.put("error", "超时：连接服务器失败");
        failures.add(failure1);
        
        Map<String, String> failure2 = new HashMap<>();
        failure2.put("caseId", "CASE-002");
        failure2.put("caseName", "订单提交测试");
        failure2.put("error", "断言失败：期望200，实际500");
        failures.add(failure2);
        
        return failures;
    }
    
    private Map<String, Object> createMockPerformanceMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("avgResponseTime", 125.5);
        metrics.put("maxResponseTime", 500.0);
        metrics.put("minResponseTime", 50.0);
        metrics.put("throughput", 100.0);
        return metrics;
    }
}
