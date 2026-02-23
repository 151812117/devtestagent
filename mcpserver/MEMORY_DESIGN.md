# MCP Server 记忆系统设计方案

## 1. 设计目标

为研发支持智能体系统提供完整的记忆管理功能，支持：
- 用户历史操作记录
- 会话上下文保持
- 长期偏好学习
- 智能检索和召回

## 2. 记忆分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                     记忆访问层 (API)                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │  写入记忆    │  │  读取记忆    │  │    记忆搜索         │ │
│  │ writeMemory │  │ readMemory  │  │   searchMemory      │ │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘ │
└─────────┼────────────────┼────────────────────┼────────────┘
          │                │                    │
          ▼                ▼                    ▼
┌─────────────────────────────────────────────────────────────┐
│                     记忆管理层                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │  短期记忆    │  │  长期记忆    │  │     记忆融合        │ │
│  │  (工作记忆)  │  │  (永久存储)  │  │   (Memory Fusion)   │ │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬──────────┘ │
└─────────┼────────────────┼────────────────────┼────────────┘
          │                │                    │
          ▼                ▼                    ▼
┌─────────────────────────────────────────────────────────────┐
│                     存储层                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │  内存缓存    │  │  文件存储    │  │    索引存储         │ │
│  │ (Concurrent │  │   (JSON)     │  │   (关键词索引)       │ │
│  │   HashMap)  │  │              │  │                     │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## 3. 记忆内容结构

### 3.1 记忆条目 (MemoryEntry)

```json
{
  "id": "唯一标识",
  "userId": "用户ID",
  "sessionId": "会话ID",
  "timestamp": "时间戳",
  "type": "记忆类型",
  "action": "操作类型",
  "target": "操作对象",
  "content": "具体内容",
  "metadata": {
    "importance": "重要程度 (1-10)",
    "category": "分类标签",
    "keywords": ["关键词列表"],
    "expiration": "过期时间"
  }
}
```

### 3.2 记忆类型

1. **EPHEMERAL** - 临时记忆（单次会话）
   - 当前对话上下文
   - 临时参数设置
   - 会话状态

2. **SHORT_TERM** - 短期记忆（最近7天）
   - 近期操作记录
   - 频繁访问的数据
   - 用户偏好变化

3. **LONG_TERM** - 长期记忆（永久）
   - 用户固定偏好
   - 历史项目信息
   - 重要的配置变更

4. **SEMANTIC** - 语义记忆
   - 概念性知识
   - 最佳实践
   - 常用模式

## 4. 记忆管理原理

### 4.1 短期记忆管理

**存储策略：**
- 使用 ConcurrentHashMap 存储在内存中
- 按 userId + sessionId 索引
- LRU 淘汰策略，最大容量 1000 条/用户

**生命周期：**
- 创建：用户操作时自动记录
- 读取：每次交互时更新访问时间
- 淘汰：超过7天未访问自动清理

**数据内容：**
```json
{
  "recentOperations": [
    {
      "action": "applyResource",
      "target": "development",
      "result": "success",
      "timestamp": "2026-02-23T10:00:00",
      "details": {...}
    }
  ],
  "sessionContext": {
    "currentTask": "env_management",
    "pendingConfirmation": false,
    "lastActivity": "2026-02-23T10:05:00"
  }
}
```

### 4.2 长期记忆管理

**存储策略：**
- 使用 JSON 文件持久化存储
- 文件路径：data/memory/{userId}.json
- 定期备份到 .bak 文件

**记忆巩固机制：**
- 短期记忆中重要度 > 7 的条目自动转为长期记忆
- 用户显式标记的重要操作
- 周期性总结提取的模式和偏好

**数据内容：**
```json
{
  "userProfile": {
    "defaultEnvType": "development",
    "preferredTestFramework": "junit",
    "commonTestScenarios": ["login", "payment"],
    "notificationPreference": "email"
  },
  "historicalTasks": [
    {
      "taskId": "task_xxx",
      "taskType": "env_management",
      "frequency": 15,
      "lastExecuted": "2026-02-20T10:00:00"
    }
  ],
  "learnedPatterns": [
    {
      "pattern": "申请环境后通常需要接口测试",
      "confidence": 0.85,
      "triggerAction": "applyResource"
    }
  ]
}
```

### 4.3 记忆检索机制

**时间维度检索：**
- 最近 N 条记录
- 指定时间范围
- 特定时间段的模式

**内容维度检索：**
- 关键词匹配（tf-idf 算法）
- 语义相似度（可选：向量检索）
- 标签过滤

**关联检索：**
- 相似用户的行为
- 关联操作的上下文
- 因果关系的推理

## 5. 实现要点

### 5.1 写入记忆 (writeMemory)

```java
1. 构建 MemoryEntry 对象
2. 写入短期记忆缓存
3. 判断是否满足长期记忆条件
4. 如满足，追加到长期记忆文件
5. 更新索引
6. 触发记忆融合（可选）
```

### 5.2 读取记忆 (readMemory)

```java
1. 从短期记忆缓存读取最近记录
2. 合并长期记忆中的偏好设置
3. 按时间倒序排序
4. 生成记忆上下文摘要
5. 返回给调用方
```

### 5.3 记忆清理策略

**定期清理任务（每天执行）：**
- 清理过期（>7天）的短期记忆
- 压缩长期记忆文件（去除重复）
- 重建索引

**动态清理：**
- 内存使用超过阈值时触发 LRU 淘汰
- 用户主动清除某类记忆

## 6. API 设计

### writeMemory

```json
{
  "userId": "user_001",
  "sessionId": "session_001",
  "action": "applyResource",
  "target": "development",
  "result": "success",
  "details": {
    "envType": "development",
    "cpu": 4,
    "memory": 8
  },
  "importance": 5
}
```

### readMemory

```json
// 请求
{
  "userId": "user_001",
  "sessionId": "session_001",
  "limit": 10
}

// 响应
{
  "recentOperations": [...],
  "userProfile": {...},
  "sessionContext": {...}
}
```

## 7. 数据存储示例

```
data/
└── memory/
    ├── user_001.json          # 用户001的长期记忆
    ├── user_001.json.bak      # 备份文件
    ├── index/
    │   └── keyword_index.json # 关键词索引
    └── temp/
        └── session_xxx.tmp    # 临时会话文件
```
