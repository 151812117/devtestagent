package com.example.mcpserver.mcp;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * MCP 工具信息
 */
@Data
@Builder
public class McpToolInfo {
    /**
     * 工具名称
     */
    private String name;
    
    /**
     * 工具描述
     */
    private String description;
    
    /**
     * 输入参数 Schema
     */
    private Map<String, Object> inputSchema;
}
