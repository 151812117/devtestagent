# MCP Server

基于 Solon 框架的 MCP Server，使用注解方式暴露工具，支持 SSE 协议。

## 架构特点

### 注解驱动
使用自定义注解简化 MCP 工具开发：

```java
@McpServerEndpoint(path = "/mcp", name = "test-tools", version = "1.0.0")
public class TestTools {
    
    @ToolMapping(
        name = "createBatch",
        description = "创建测试批次",
        inputType = CreateBatchRequest.class
    )
    public CreateBatchResponse createBatch(CreateBatchRequest request) {
        // 业务逻辑
    }
}
```

### MCP 协议端点

| 端点 | 说明 |
|------|------|
| GET /mcp | SSE 端点 |
| POST /mcp/tools/list | 获取工具列表 |
| POST /mcp/tools/call | 调用工具 |

### 自动参数 Schema
MCP Server 自动根据 `inputType` 生成 JSON Schema，大模型会自动获取参数信息。

## 测试批次管理工具

### 1. createBatch - 创建测试批次
**参数：**
- systemName: 被测系统名
- systemCode: 被测系统编号
- batchLabel: 批次标识

### 2. addCasesToBatch - 添加案例到批次
**参数：**
- caseIds: 案例编号列表
- batchId: 批次编号

### 3. executeBatch - 执行批次
**参数：**
- batchId: 批次编号
- systemCode: 系统编号

### 4. analyzeBatchResult - 批次结果分析
**参数：**
- executionId: 执行编号

## 记忆工具

### readMemory - 读取记忆
### writeMemory - 写入记忆

## 快速开始

```bash
# 编译
mvn clean package

# 运行
java -jar target/mcpserver-1.0.0.jar

# 测试端点
curl http://localhost:3000/mcp/tools/list
```

## 大模型自动获取参数

MCP 协议通过 `tools/list` 端点返回工具的 JSON Schema，包含：
- 工具名称
- 工具描述
- 输入参数的结构和类型

**不需要在提示词中详细说明每个参数**，大模型会自动获取这些信息。
