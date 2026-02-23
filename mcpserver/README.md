# MCP Server

基于 Solon 框架的 MCP Server，为研发支持智能体系统提供工具服务。

## 功能特性

### 1. 环境资源管理
- **applyResource** - 申请环境资源
- **recycleResource** - 回收环境资源

### 2. 测试工具
- **autoInterfaceTest** - 自动化接口测试
- **autoUITest** - 自动化界面测试
- **resultAnalysis** - 测试结果分析

### 3. 记忆系统
- **readMemory** - 读取用户记忆
- **writeMemory** - 写入用户记忆

## 记忆系统设计

### 记忆分层
1. **临时记忆 (EPHEMERAL)** - 1小时有效期
2. **短期记忆 (SHORT_TERM)** - 7天有效期，内存存储
3. **长期记忆 (LONG_TERM)** - 永久存储，文件存储
4. **语义记忆 (SEMANTIC)** - 概念性知识

### 存储策略
- 短期记忆：ConcurrentHashMap + LRU 淘汰
- 长期记忆：JSON 文件持久化 (data/memory/{userId}.json)

### 记忆管理
- 自动过期清理
- 重要性评估（1-10）
- 关键词提取
- 访问统计

## 快速开始

### 1. 编译运行

```bash
# 编译
mvn clean package

# 运行
java -jar target/mcpserver-1.0.0.jar

# 或直接使用 Maven 运行
mvn solon:run
```

### 2. 测试接口

```bash
# 健康检查
curl http://localhost:3000/tools/health

# 申请环境资源
curl -X POST http://localhost:3000/tools/applyResource \
  -H "Content-Type: application/json" \
  -d '{
    "envType": "development",
    "duration": 24,
    "cpu": 4,
    "memory": 8,
    "storage": 100,
    "instanceCount": 1,
    "purpose": "开发测试",
    "owner": "user_001"
  }'

# 写入记忆
curl -X POST http://localhost:3000/tools/writeMemory \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user_001",
    "sessionId": "session_001",
    "action": "applyResource",
    "target": "development",
    "result": "success",
    "details": "申请了开发环境",
    "importance": 7
  }'

# 读取记忆
curl -X POST http://localhost:3000/tools/readMemory \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user_001",
    "sessionId": "session_001",
    "limit": 10
  }'
```

## API 列表

| 工具 | 路径 | 方法 | 说明 |
|------|------|------|------|
| applyResource | /tools/applyResource | POST | 申请环境资源 |
| recycleResource | /tools/recycleResource | POST | 回收环境资源 |
| autoInterfaceTest | /tools/autoInterfaceTest | POST | 自动化接口测试 |
| autoUITest | /tools/autoUITest | POST | 自动化界面测试 |
| resultAnalysis | /tools/resultAnalysis | POST | 测试结果分析 |
| readMemory | /tools/readMemory | POST | 读取记忆 |
| writeMemory | /tools/writeMemory | POST | 写入记忆 |
| health | /tools/health | GET | 健康检查 |

## 技术栈

- Solon 2.8.6
- Fastjson2
- Lombok
- JLHttp (内置 HTTP 服务器)
