# 研发支持智能体系统 (DevTest Agent System)

基于 Spring AI Alibaba 的多智能体研发支持系统，支持研发环境管理和自动化测试功能。

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                      用户界面 (Frontend)                      │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                     API 控制器层                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  AgentController                                    │   │
│  │  - POST /api/agent/intent   (意图解析)               │   │
│  │  - POST /api/agent/execute  (任务执行)               │   │
│  └─────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                     服务层 (Service)                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  AgentService                                       │   │
│  │  - 管理两轮交互状态                                  │   │
│  │  - 缓存意图解析结果                                  │   │
│  └─────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                   主智能体 (Master Agent)                    │
│              Plan-and-Execute 模式                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  职责：                                             │   │
│  │  1. 任务规划 (Task Planning)                        │   │
│  │  2. 记忆整合 (Memory Integration)                   │   │
│  │  3. 智能路由 (Intelligent Routing)                  │   │
│  └─────────────────────────────────────────────────────┘   │
└──────────┬───────────────────────────────┬──────────────────┘
           │                               │
           ▼                               ▼
┌─────────────────────┐      ┌─────────────────────────────┐
│   环境管理智能体      │      │       测试智能体             │
│  (EnvManagementAgent)│      │      (TestAgent)            │
│    ReAct 模式        │      │       ReAct 模式            │
│  ┌───────────────┐  │      │  ┌─────────────────────┐    │
│  │ applyResource │  │      │  │ autoInterfaceTest   │    │
│  │ recycleResource│  │      │  │ autoUITest          │    │
│  └───────────────┘  │      │  │ resultAnalysis      │    │
└─────────────────────┘      │  └─────────────────────┘    │
                             └─────────────────────────────┘
                                       │
                                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    MCP 工具客户端                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  McpToolClient                                      │   │
│  │  - 调用 MCP Server 的工具                            │   │
│  │  - applyResource, recycleResource                   │   │
│  │  - autoInterfaceTest, autoUITest                    │   │
│  │  - resultAnalysis                                   │   │
│  └─────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    MCP Server (外部服务)                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  记忆服务                                             │   │
│  │  - readMemory  (读取用户历史)                         │   │
│  │  - writeMemory (异步写入记忆)                         │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## 核心特性

### 1. 多智能体架构
- **主智能体 (Master Agent)**: 采用 Plan-and-Execute 模式，负责任务规划和智能路由
- **子智能体**: 采用 ReAct 模式，执行具体任务
  - **环境管理智能体**: 处理环境资源申请、回收
  - **测试智能体**: 处理自动化接口测试、界面测试、结果分析

### 2. 两轮交互流程
```
┌─────────┐    用户提问    ┌──────────┐    读取记忆    ┌──────────┐
│  用户   │ ────────────> │ 主智能体 │ ────────────> │ 记忆服务 │
└─────────┘               └────┬─────┘               └──────────┘
                               │
                               │ 任务规划
                               ▼
                         ┌──────────┐
                         │ 子智能体 │
                         └────┬─────┘
                              │ 解析意图
                              ▼
                         ┌──────────┐
                         │ 返回JSON │
                         └────┬─────┘
                              │
                              ▼
┌─────────┐   JSON展示      ┌──────────┐
│  用户   │ <────────────── │   前端   │
└────┬────┘                 └──────────┘
     │
     │ 确认/修改参数
     ▼
┌─────────┐    提交确认    ┌──────────┐
│  用户   │ ────────────> │ 主智能体 │
└─────────┘               └────┬─────┘
                               │
                               │ 路由
                               ▼
                         ┌──────────┐
                         │ 子智能体 │
                         └────┬─────┘
                              │ 调用MCP工具
                              ▼
                         ┌──────────┐
                         │ MCP服务  │
                         └────┬─────┘
                              │
                              │ 异步写入记忆
                              ▼
                         ┌──────────┐
                         │ 返回结果 │
                         └────┬─────┘
                              │
                              ▼
┌─────────┐    展示结果     ┌──────────┐
│  用户   │ <────────────── │   前端   │
└─────────┘                 └──────────┘
```

### 3. 记忆管理
- 使用 MCP 工具进行记忆的读写
- 支持用户历史记录查询
- 异步写入，不阻塞主流程

## 项目结构

```
dev-test-agent/
├── pom.xml                          # Maven 配置
├── README.md                        # 项目文档
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/devtestagent/
│       │       ├── DevTestAgentApplication.java    # 启动类
│       │       ├── agent/
│       │       │   ├── master/
│       │       │   │   └── MasterAgent.java        # 主智能体
│       │       │   └── sub/
│       │       │       ├── SubAgent.java           # 子智能体接口
│       │       │       ├── EnvManagementAgent.java # 环境管理智能体
│       │       │       └── TestAgent.java          # 测试智能体
│       │       ├── config/
│       │       │   └── AppConfig.java              # 应用配置
│       │       ├── controller/
│       │       │   └── AgentController.java        # API 控制器
│       │       ├── mcp/
│       │       │   ├── McpToolClient.java          # MCP 工具客户端
│       │       │   └── MemoryService.java          # 记忆服务
│       │       ├── model/
│       │       │   ├── AgentRequest.java           # 智能体请求
│       │       │   ├── AgentResponse.java          # 智能体响应
│       │       │   ├── IntentParseResult.java      # 意图解析结果
│       │       │   ├── MemoryContext.java          # 记忆上下文
│       │       │   └── TaskPlan.java               # 任务规划
│       │       └── service/
│       │           └── AgentService.java           # 业务服务
│       └── resources/
│           ├── application.yml      # 应用配置
│           ├── prompts/             # 提示词文件
│           │   ├── master-agent-system-prompt.txt
│           │   ├── env-agent-system-prompt.txt
│           │   └── test-agent-system-prompt.txt
│           └── static/
│               └── index.html       # 前端页面
```

## 快速开始

### 1. 环境要求
- JDK 17+
- Maven 3.8+
- Spring Boot 3.2+

### 2. 配置
编辑 `src/main/resources/application.yml`：

```yaml
spring:
  ai:
    dashscope:
      api-key: your-dashscope-api-key

mcp:
  server:
    url: http://localhost:3000  # MCP Server 地址
```

### 3. 构建与运行

```bash
# 编译
mvn clean compile

# 运行
mvn spring-boot:run

# 或者打包后运行
mvn clean package
java -jar target/dev-test-agent-1.0.0.jar
```

### 4. 访问系统
打开浏览器访问: http://localhost:8080

## API 接口

### 1. 意图解析 (第一轮)
```http
POST /api/agent/intent
Content-Type: application/json

{
    "query": "申请一个开发环境，CPU 4核，内存8G",
    "userId": "user_001",
    "sessionId": "session_001"
}
```

响应：
```json
{
    "responseId": "resp_xxx",
    "requestId": "req_xxx",
    "type": "INTENT_PARSE_RESULT",
    "intentResult": {
        "action": "applyResource",
        "target": "development",
        "parameters": {
            "envType": "development",
            "cpu": 4,
            "memory": 8,
            "duration": 24
        },
        "confidence": 0.9,
        "needConfirmation": true
    }
}
```

### 2. 任务执行 (第二轮)
```http
POST /api/agent/execute
Content-Type: application/json

{
    "requestId": "req_xxx",
    "query": "申请一个开发环境，CPU 4核，内存8G",
    "userId": "user_001",
    "sessionId": "session_001",
    "parameters": {
        "envType": "development",
        "cpu": 4,
        "memory": 8,
        "duration": 24
    }
}
```

响应：
```json
{
    "responseId": "resp_xxx",
    "requestId": "req_xxx",
    "type": "EXECUTION_RESULT",
    "executionResult": {
        "success": true,
        "message": "Environment operation completed successfully",
        "data": { ... },
        "taskId": "task_xxx"
    }
}
```

## 支持的 MCP 工具

### 环境管理
- `applyResource` - 申请环境资源
- `recycleResource` - 回收环境资源

### 测试
- `autoInterfaceTest` - 自动化接口测试
- `autoUITest` - 自动化界面测试
- `resultAnalysis` - 测试结果分析

### 记忆
- `readMemory` - 读取用户记忆
- `writeMemory` - 写入用户记忆

## 技术栈

- **框架**: Spring Boot 3.2
- **AI 框架**: Spring AI Alibaba
- **大模型**: 通义千问 (DashScope)
- **通信**: WebFlux (响应式编程)
- **构建工具**: Maven

## 许可证

MIT License
