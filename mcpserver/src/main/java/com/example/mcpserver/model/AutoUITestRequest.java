package com.example.mcpserver.model;

import lombok.Data;
import java.util.List;

/**
 * 自动化界面测试请求
 */
@Data
public class AutoUITestRequest {
    
    /**
     * 测试类型（web/mobile/desktop）
     */
    private String testType;
    
    /**
     * 目标URL或应用路径
     */
    private String targetUrl;
    
    /**
     * 浏览器类型（chrome/firefox/safari/edge）
     */
    private String browser;
    
    /**
     * 测试场景
     */
    private String testScenario;
    
    /**
     * 测试用例列表
     */
    private List<String> testCases;
    
    /**
     * 视口大小
     */
    private String viewport;
    
    /**
     * 是否无头模式
     */
    private Boolean headless;
    
    /**
     * 失败时是否截图
     */
    private Boolean screenshotOnFailure;
}
