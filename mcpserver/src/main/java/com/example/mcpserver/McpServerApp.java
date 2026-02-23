package com.example.mcpserver;

import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;

/**
 * MCP Server 启动类
 */
@SolonMain
public class McpServerApp {
    public static void main(String[] args) {
        Solon.start(McpServerApp.class, args, app -> {
            // 设置端口为 3000
            app.cfg().setProperty("server.port", "3000");
            System.out.println("MCP Server started on port 3000");
        });
    }
}
