package com.example.mcpserver.mcp;

import org.noear.solon.annotation.Component;

import java.lang.annotation.*;

/**
 * MCP Server 端点注解
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface McpServerEndpoint {
    /**
     * SSE 端点路径
     */
    String path() default "/mcp";
    
    /**
     * Server 名称
     */
    String name() default "mcp-server";
    
    /**
     * Server 版本
     */
    String version() default "1.0.0";
}
