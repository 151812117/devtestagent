package com.example.mcpserver;

import com.example.mcpserver.mcp.McpServerHandler;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;

/**
 * MCP Server 启动类
 */
@Slf4j
@SolonMain
public class McpServerApp {
    
    public static void main(String[] args) {
        // 创建共享的 handler 实例
        final McpServerHandler mcpHandler = new McpServerHandler("dev-test-mcp", "1.0.0");
        
        Solon.start(McpServerApp.class, args, app -> {
            // 设置端口为 3000
            app.cfg().setProperty("server.port", "3000");
            
            // 注册 MCP Server 处理器
            app.get("/mcp", mcpHandler::handle);
            app.get("/mcp/tools/list", mcpHandler::handle);
            app.post("/mcp/tools/call", mcpHandler::handle);
            app.post("/mcp/tools/list", mcpHandler::handle);
            
            log.info("MCP Server routes registered");
        });
        
        // Solon 启动完成后初始化 MCP Server
        // 使用延迟初始化确保所有 Bean 都已加载
        new Thread(() -> {
            try {
                Thread.sleep(2000); // 等待 2 秒确保所有 Bean 加载完成
                log.info("Initializing MCP Server handler...");
                mcpHandler.init();
                log.info("MCP Server tools initialized, count: {}", mcpHandler.getToolCount());
            } catch (Exception e) {
                log.error("Failed to initialize MCP Server: {}", e.getMessage(), e);
            }
        }).start();
    }
}
