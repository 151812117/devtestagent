package com.example.mcpserver.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 自动化接口测试请求
 */
@Data
public class AutoInterfaceTestRequest {
    
    /**
     * 测试类型（api/graphql/grpc/websocket）
     */
    private String testType;
    
    /**
     * 目标URL
     */
    private String targetUrl;
    
    /**
     * 测试场景
     */
    private String testScenario;
    
    /**
     * 测试用例列表
     */
    private List<String> testCases;
    
    /**
     * 请求头
     */
    private Map<String, String> headers;
    
    /**
     * 超时时间（秒）
     */
    private Integer timeout;
    
    /**
     * 并发用户数（压力测试）
     */
    private Integer concurrentUsers;
    
    /**
     * 请求数量
     */
    private Integer requestCount;
}
