package com.example.mcpserver.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.example.mcpserver.model.*;
import java.util.List;
import com.example.mcpserver.service.EnvService;
import com.example.mcpserver.service.MemoryService;
import com.example.mcpserver.service.TestService;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.*;

import java.util.Map;

/**
 * MCP 工具控制器
 * 提供所有 MCP 工具的 HTTP 接口
 */
@Slf4j
@Controller
@Mapping("/tools")
public class ToolController {
    
    @Inject
    private EnvService envService;
    
    @Inject
    private TestService testService;
    
    @Inject
    private MemoryService memoryService;
    
    /**
     * 从请求体中提取参数
     * 支持两种格式：
     * 1. 直接传递请求对象
     * 2. 包装在 params 字段中: {"params": {...}}
     */
    private <T> T extractParams(Map<String, Object> body, Class<T> clazz) {
        if (body == null) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                return null;
            }
        }
        
        try {
            // 如果包含 params 字段，提取它
            if (body.containsKey("params")) {
                Object params = body.get("params");
                if (params instanceof Map) {
                    // 先将 Map 转为 JSONObject，再转为目标类型
                    JSONObject jsonObject = new JSONObject((Map<String, Object>) params);
                    return jsonObject.toJavaObject(clazz);
                }
            }
            
            // 否则直接使用整个 body
            JSONObject jsonObject = new JSONObject(body);
            return jsonObject.toJavaObject(clazz);
        } catch (Exception e) {
            log.error("Failed to extract params: {}", e.getMessage());
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                return null;
            }
        }
    }
    
    // ==================== 环境资源管理工具 ====================
    
    /**
     * 申请环境资源
     * POST /tools/applyResource
     */
    @Post
    @Mapping("/applyResource")
    public CommonResponse<ApplyResourceResponse> applyResource(@Body Map<String, Object> body) {
        log.info("[MCP] applyResource called");
        ApplyResourceRequest request = extractParams(body, ApplyResourceRequest.class);
        return envService.applyResource(request);
    }
    
    /**
     * 回收环境资源
     * POST /tools/recycleResource
     */
    @Post
    @Mapping("/recycleResource")
    public CommonResponse<RecycleResourceResponse> recycleResource(@Body Map<String, Object> body) {
        log.info("[MCP] recycleResource called");
        RecycleResourceRequest request = extractParams(body, RecycleResourceRequest.class);
        return envService.recycleResource(request);
    }
    
    // ==================== 测试工具 ====================
    
    /**
     * 自动化接口测试
     * POST /tools/autoInterfaceTest
     */
    @Post
    @Mapping("/autoInterfaceTest")
    public CommonResponse<AutoInterfaceTestResponse> autoInterfaceTest(@Body Map<String, Object> body) {
        log.info("[MCP] autoInterfaceTest called");
        AutoInterfaceTestRequest request = extractParams(body, AutoInterfaceTestRequest.class);
        return testService.autoInterfaceTest(request);
    }
    
    /**
     * 自动化界面测试
     * POST /tools/autoUITest
     */
    @Post
    @Mapping("/autoUITest")
    public CommonResponse<AutoUITestResponse> autoUITest(@Body Map<String, Object> body) {
        log.info("[MCP] autoUITest called");
        AutoUITestRequest request = extractParams(body, AutoUITestRequest.class);
        return testService.autoUITest(request);
    }
    
    /**
     * 测试结果分析
     * POST /tools/resultAnalysis
     */
    @Post
    @Mapping("/resultAnalysis")
    public CommonResponse<ResultAnalysisResponse> resultAnalysis(@Body Map<String, Object> body) {
        log.info("[MCP] resultAnalysis called");
        ResultAnalysisRequest request = extractParams(body, ResultAnalysisRequest.class);
        return testService.resultAnalysis(request);
    }
    
    // ==================== 记忆工具 ====================
    
    /**
     * 读取记忆
     * POST /tools/readMemory
     */
    @Post
    @Mapping("/readMemory")
    public CommonResponse<ReadMemoryResponse> readMemory(@Body Map<String, Object> body) {
        log.info("[MCP] readMemory called");
        ReadMemoryRequest request = extractParams(body, ReadMemoryRequest.class);
        // 设置默认值
        if (request.getUserId() == null) {
            request.setUserId("anonymous");
        }
        if (request.getSessionId() == null) {
            request.setSessionId("default");
        }
        if (request.getLimit() == null) {
            request.setLimit(10);
        }
        return memoryService.readMemory(request);
    }
    
    /**
     * 写入记忆
     * POST /tools/writeMemory
     */
    @Post
    @Mapping("/writeMemory")
    public CommonResponse<WriteMemoryResponse> writeMemory(@Body Map<String, Object> body) {
        log.info("[MCP] writeMemory called");
        WriteMemoryRequest request = extractParams(body, WriteMemoryRequest.class);
        // 设置默认值
        if (request.getUserId() == null) {
            request.setUserId("anonymous");
        }
        if (request.getSessionId() == null) {
            request.setSessionId("default");
        }
        return memoryService.writeMemory(request);
    }
    
    // ==================== 健康检查 ====================
    
    /**
     * 健康检查
     * GET /tools/health
     */
    @Get
    @Mapping("/health")
    public CommonResponse<String> health() {
        return CommonResponse.ok("MCP Server is running");
    }
}
