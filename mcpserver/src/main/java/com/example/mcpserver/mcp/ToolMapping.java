package com.example.mcpserver.mcp;

import java.lang.annotation.*;

/**
 * MCP 工具映射注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolMapping {
    /**
     * 工具名称
     */
    String name();
    
    /**
     * 工具描述
     */
    String description();
    
    /**
     * 参数类型（用于生成 JSON Schema）
     */
    Class<?> inputType() default Void.class;
}
