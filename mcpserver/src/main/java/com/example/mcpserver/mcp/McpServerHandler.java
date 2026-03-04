package com.example.mcpserver.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.Solon;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.BeanWrap;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Handler;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MCP Server 处理器
 */
@Slf4j
public class McpServerHandler {
    
    private final String serverName;
    private final String serverVersion;
    private final List<McpToolInfo> tools = new CopyOnWriteArrayList<>();
    private final Map<String, ToolHandler> toolHandlers = new ConcurrentHashMap<>();
    
    public McpServerHandler(String serverName, String serverVersion) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
    }
    
    /**
     * 初始化并注册工具
     */
    public void init() {
        AppContext appContext = Solon.context();
        
        log.info("[MCP] Starting tool scan...");
        
        // 扫描所有 Bean
        Map<String, BeanWrap> beanMap = new HashMap<>();
        appContext.beanForeach((name, wrap) -> {
            log.debug("[MCP] Found bean: name={}, class={}", name, 
                wrap.clz() != null ? wrap.clz().getSimpleName() : "null");
            beanMap.put(name, wrap);
        });
        
        log.info("[MCP] Total beans found: {}", beanMap.size());
        
        for (BeanWrap wrap : beanMap.values()) {
            Class<?> clazz = wrap.clz();
            if (clazz == null) continue;
            
            // 检查类是否有 @McpServerEndpoint
            McpServerEndpoint endpoint = clazz.getAnnotation(McpServerEndpoint.class);
            if (endpoint == null) {
                log.debug("[MCP] Class {} does not have @McpServerEndpoint", clazz.getSimpleName());
                continue;
            }
            
            log.info("[MCP] Found @McpServerEndpoint class: {}", clazz.getName());
            
            Object instance = wrap.get();
            
            // 扫描方法
            for (Method method : clazz.getDeclaredMethods()) {
                ToolMapping toolMapping = method.getAnnotation(ToolMapping.class);
                if (toolMapping == null) continue;
                
                String toolName = toolMapping.name();
                String description = toolMapping.description();
                Class<?> inputType = toolMapping.inputType();
                
                // 生成 JSON Schema
                Map<String, Object> schema = generateJsonSchema(inputType);
                
                // 注册工具
                McpToolInfo toolInfo = McpToolInfo.builder()
                    .name(toolName)
                    .description(description)
                    .inputSchema(schema)
                    .build();
                
                tools.add(toolInfo);
                toolHandlers.put(toolName, new ToolHandler(instance, method));
                
                log.info("MCP Tool registered: name={}, description={}", toolName, description);
            }
        }
        
        log.info("MCP Server initialized: name={}, version={}, tools={}", 
            serverName, serverVersion, tools.size());
    }
    
    /**
     * 获取已注册工具数量
     */
    public int getToolCount() {
        return tools.size();
    }
    
    /**
     * 处理请求
     */
    public void handle(Context ctx) throws Exception {
        String path = ctx.path();
        
        if (path.endsWith("/tools/list")) {
            handleToolsList(ctx);
        } else if (path.endsWith("/tools/call")) {
            handleToolsCall(ctx);
        } else {
            // 默认返回工具列表
            handleToolsList(ctx);
        }
    }
    
    /**
     * 生成 JSON Schema
     */
    private Map<String, Object> generateJsonSchema(Class<?> inputType) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        
        if (inputType != null && inputType != Void.class) {
            for (java.lang.reflect.Field field : inputType.getDeclaredFields()) {
                Map<String, Object> fieldSchema = new HashMap<>();
                fieldSchema.put("type", getJsonType(field.getType()));
                fieldSchema.put("description", field.getName());
                
                properties.put(field.getName(), fieldSchema);
                
                // 假设所有字段都是必填的
                required.add(field.getName());
            }
        }
        
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        
        return schema;
    }
    
    private String getJsonType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class) return "integer";
        if (type == long.class || type == Long.class) return "integer";
        if (type == double.class || type == Double.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type.isArray() || Collection.class.isAssignableFrom(type)) return "array";
        return "object";
    }
    
    /**
     * 处理 tools/list 请求
     */
    private void handleToolsList(Context ctx) throws Exception {
        ctx.contentType("application/json");
        
        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);
        
        ctx.output(JSON.toJSONString(result));
    }
    
    /**
     * 处理 tools/call 请求
     */
    private void handleToolsCall(Context ctx) throws Exception {
        ctx.contentType("application/json");
        
        String body = ctx.body();
        log.debug("Tools call request body: {}", body);
        
        JSONObject request = JSON.parseObject(body);
        
        String toolName = request.getString("name");
        JSONObject arguments = request.getJSONObject("arguments");
        
        log.info("Tool call: name={}, arguments={}", toolName, arguments);
        log.info("Available tools: {}", toolHandlers.keySet());
        
        ToolHandler handler = toolHandlers.get(toolName);
        if (handler == null) {
            log.error("Tool not found: {}", toolName);
            ctx.output(JSON.toJSONString(createErrorResult("Tool not found: " + toolName)));
            return;
        }
        
        try {
            Object result = handler.invoke(arguments);
            ctx.output(JSON.toJSONString(createSuccessResult(result)));
        } catch (Exception e) {
            log.error("Tool execution failed: {}", e.getMessage(), e);
            ctx.output(JSON.toJSONString(createErrorResult(e.getMessage())));
        }
    }
    
    private Map<String, Object> createSuccessResult(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", data);
        return result;
    }
    
    private Map<String, Object> createErrorResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", message);
        return result;
    }
    
    /**
     * 工具处理器
     */
    private static class ToolHandler {
        private final Object instance;
        private final Method method;
        
        public ToolHandler(Object instance, Method method) {
            this.instance = instance;
            this.method = method;
        }
        
        public Object invoke(JSONObject arguments) throws Exception {
            // 获取方法参数类型
            Parameter[] parameters = method.getParameters();
            if (parameters.length == 0) {
                return method.invoke(instance);
            }
            
            // 将 JSON 参数转换为方法参数
            Class<?> paramType = parameters[0].getType();
            Object arg = arguments.toJavaObject(paramType);
            return method.invoke(instance, arg);
        }
    }
}
