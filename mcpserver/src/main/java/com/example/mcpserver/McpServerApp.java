package com.example.mcpserver;

import com.example.mcpserver.mcp.McpServerHandler;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;
import org.noear.solon.core.event.AppBeanLoadEndEvent;
import org.noear.solon.core.event.EventBus;

/**
 * MCP Server 启动类
 */
@Slf4j
@SolonMain
public class McpServerApp {
    public static void main(String[] args) {
        Solon.start(McpServerApp.class, args, app -> {
            // 设置端口为 3000
            app.cfg().setProperty("server.port", "3000");
            
            // 注册 MCP Server 处理器
            McpServerHandler mcpHandler = new McpServerHandler("dev-test-mcp", "1.0.0");
            
            // 注册路由
            app.get("/mcp", mcpHandler::handle);
            app.get("/mcp/tools/list", mcpHandler::handle);
            app.post("/mcp/tools/call", mcpHandler::handle);
            app.post("/mcp/tools/list", mcpHandler::handle);
            
            log.info("MCP Server started on port 3000");
        });
        
        // Solon 启动后初始化 MCP Server
        EventBus.subscribe(AppBeanLoadEndEvent.class, event -> {
            McpServerHandler handler = new McpServerHandler("dev-test-mcp", "1.0.0");
            handler.init();
        });
    }
}
