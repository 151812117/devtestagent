package com.example.mcpserver.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.TypeReference;
import com.example.mcpserver.mcp.ToolMapping;
import com.example.mcpserver.model.*;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 记忆服务
 * 实现短期记忆和长期记忆的管理
 */
@Slf4j
@Component
public class MemoryService {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // 短期记忆：内存缓存，按 userId 索引
    private final Map<String, List<MemoryEntry>> shortTermMemory = new ConcurrentHashMap<>();
    
    // 会话上下文：按 sessionId 索引
    private final Map<String, ReadMemoryResponse.SessionContext> sessionContext = new ConcurrentHashMap<>();
    
    // 长期记忆存储目录
    private Path longTermMemoryDir;
    
    // 短期记忆最大条数/用户
    private static final int MAX_SHORT_TERM_MEMORY = 100;
    
    // 短期记忆有效期（天）
    private static final int SHORT_TERM_DAYS = 7;
    
    @Init
    public void init() {
        try {
            // 初始化长期记忆存储目录
            longTermMemoryDir = Paths.get("data", "memory");
            Files.createDirectories(longTermMemoryDir);
            log.info("Memory service initialized. Long-term memory dir: {}", longTermMemoryDir);
        } catch (IOException e) {
            log.error("Failed to initialize memory directory", e);
            throw new RuntimeException("Failed to initialize memory service", e);
        }
    }
    
    /**
     * 写入记忆
     */
    @ToolMapping(
        name = "writeMemory",
        description = "写入用户记忆，记录用户的操作历史",
        inputType = WriteMemoryRequest.class
    )
    public WriteMemoryResponse writeMemory(WriteMemoryRequest request) {
        log.info("Writing memory: userId={}, action={}", request.getUserId(), request.getAction());
        
        String memoryId = generateMemoryId();
        String now = LocalDateTime.now().format(FORMATTER);
        
        // 确定记忆类型
        int importance = request.getImportance() != null ? request.getImportance() : 5;
        String memoryType = determineMemoryType(importance, request.getPersistLongTerm());
        
        // 构建记忆条目
        MemoryEntry entry = MemoryEntry.builder()
            .id(memoryId)
            .userId(request.getUserId())
            .sessionId(request.getSessionId())
            .timestamp(request.getTimestamp() != null ? request.getTimestamp() : now)
            .memoryType(memoryType)
            .action(request.getAction())
            .target(request.getTarget())
            .result(request.getResult())
            .details(request.getDetails())
            .importance(importance)
            .category(request.getCategory())
            .keywords(request.getKeywords())
            .expirationTime(calculateExpirationTime(memoryType))
            .accessCount(1)
            .lastAccessTime(now)
            .build();
        
        // 存储记忆
        if ("LONG_TERM".equals(memoryType) || "SEMANTIC".equals(memoryType)) {
            // 长期记忆：写入文件
            saveToLongTermMemory(entry);
        } else {
            // 短期记忆：写入内存
            saveToShortTermMemory(entry);
        }
        
        // 更新会话上下文
        updateSessionContext(request);
        
        WriteMemoryResponse response = WriteMemoryResponse.builder()
            .memoryId(memoryId)
            .success(true)
            .storageType(memoryType)
            .message("Memory saved successfully")
            .build();
        
        log.info("Memory written: memoryId={}, type={}", memoryId, memoryType);
        return response;
    }
    
    /**
     * 读取记忆
     */
    @ToolMapping(
        name = "readMemory",
        description = "读取用户记忆，获取历史操作记录",
        inputType = ReadMemoryRequest.class
    )
    public ReadMemoryResponse readMemory(ReadMemoryRequest request) {
        log.info("Reading memory: userId={}, sessionId={}", request.getUserId(), request.getSessionId());
        
        int limit = request.getLimit() != null ? request.getLimit() : 10;
        boolean includeLongTerm = request.getIncludeLongTerm() == null || request.getIncludeLongTerm();
        
        // 防御 null userId
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";
        
        // 读取短期记忆
        List<MemoryEntry> shortTermList = getShortTermMemories(userId, limit, request.getMemoryType(), request.getCategory());
        
        // 读取长期记忆（概要）
        Map<String, Object> longTermProfile = includeLongTerm ? getLongTermProfile(userId) : new HashMap<>();
        
        // 获取会话上下文
        ReadMemoryResponse.SessionContext context = sessionContext.getOrDefault(
            sessionId, 
            ReadMemoryResponse.SessionContext.builder()
                .currentTask("unknown")
                .pendingConfirmation(false)
                .lastActivity(LocalDateTime.now().format(FORMATTER))
                .build()
        );
        
        ReadMemoryResponse response = ReadMemoryResponse.builder()
            .userId(userId)
            .sessionId(sessionId)
            .shortTermMemories(shortTermList)
            .longTermProfile(longTermProfile)
            .sessionContext(context)
            .totalCount(shortTermList.size())
            .data(buildMemoryData(shortTermList, longTermProfile))
            .build();
        
        return response;
    }
    
    /**
     * 确定记忆类型
     */
    private String determineMemoryType(int importance, Boolean persistLongTerm) {
        if (persistLongTerm != null && persistLongTerm) {
            return "LONG_TERM";
        }
        if (importance >= 8) {
            return "LONG_TERM";
        } else if (importance >= 5) {
            return "SHORT_TERM";
        } else {
            return "EPHEMERAL";
        }
    }
    
    /**
     * 计算过期时间
     */
    private String calculateExpirationTime(String memoryType) {
        LocalDateTime now = LocalDateTime.now();
        switch (memoryType) {
            case "EPHEMERAL":
                return now.plusHours(1).format(FORMATTER);
            case "SHORT_TERM":
                return now.plusDays(SHORT_TERM_DAYS).format(FORMATTER);
            case "LONG_TERM":
            case "SEMANTIC":
                return now.plusYears(10).format(FORMATTER);
            default:
                return now.plusDays(1).format(FORMATTER);
        }
    }
    
    /**
     * 保存到短期记忆
     */
    private void saveToShortTermMemory(MemoryEntry entry) {
        String userId = entry.getUserId();
        // 防御 null userId
        if (userId == null) {
            userId = "anonymous";
        }
        shortTermMemory.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()));
        
        List<MemoryEntry> list = shortTermMemory.get(userId);
        list.add(0, entry); // 新记忆添加到头部
        
        // 限制容量，超出则移除最旧的
        while (list.size() > MAX_SHORT_TERM_MEMORY) {
            list.remove(list.size() - 1);
        }
    }
    
    /**
     * 保存到长期记忆
     */
    private void saveToLongTermMemory(MemoryEntry entry) {
        try {
            String userId = entry.getUserId();
            // 防御 null userId
            if (userId == null) {
                userId = "anonymous";
            }
            Path filePath = longTermMemoryDir.resolve(userId + ".json");
            
            List<MemoryEntry> memories = new ArrayList<>();
            if (Files.exists(filePath)) {
                String content = Files.readString(filePath, StandardCharsets.UTF_8);
                memories = JSON.parseObject(content, new TypeReference<List<MemoryEntry>>() {});
            }
            
            memories.add(0, entry);
            
            // 写入文件
            Files.writeString(filePath, JSON.toJSONString(memories, JSONWriter.Feature.PrettyFormat), StandardCharsets.UTF_8);
            
        } catch (IOException e) {
            log.error("Failed to save long-term memory", e);
        }
    }
    
    /**
     * 获取短期记忆
     */
    private List<MemoryEntry> getShortTermMemories(String userId, int limit, String memoryType, String category) {
        // 防御 null key
        if (userId == null) {
            userId = "anonymous";
        }
        List<MemoryEntry> list = shortTermMemory.getOrDefault(userId, new ArrayList<>());
        
        // 清理过期记忆
        String now = LocalDateTime.now().format(FORMATTER);
        list.removeIf(entry -> entry.getExpirationTime() != null && entry.getExpirationTime().compareTo(now) < 0);
        
        // 过滤
        return list.stream()
            .filter(entry -> memoryType == null || memoryType.equals(entry.getMemoryType()))
            .filter(entry -> category == null || category.equals(entry.getCategory()))
            .limit(limit)
            .peek(entry -> {
                // 更新访问统计
                entry.setAccessCount(entry.getAccessCount() + 1);
                entry.setLastAccessTime(now);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 获取长期记忆概要
     */
    private Map<String, Object> getLongTermProfile(String userId) {
        Map<String, Object> profile = new HashMap<>();
        
        // 防御 null userId
        if (userId == null) {
            userId = "anonymous";
        }
        
        try {
            Path filePath = longTermMemoryDir.resolve(userId + ".json");
            if (Files.exists(filePath)) {
                String content = Files.readString(filePath, StandardCharsets.UTF_8);
                List<MemoryEntry> memories = JSON.parseObject(content, new TypeReference<List<MemoryEntry>>() {});
                
                // 统计信息
                profile.put("totalMemories", memories.size());
                profile.put("recentHighImportance", memories.stream()
                    .filter(m -> m.getImportance() >= 8)
                    .limit(5)
                    .map(MemoryEntry::getAction)
                    .collect(Collectors.toList()));
                
                // 常用操作类型
                Map<String, Long> actionCounts = memories.stream()
                    .collect(Collectors.groupingBy(MemoryEntry::getAction, Collectors.counting()));
                profile.put("commonActions", actionCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList()));
            }
        } catch (IOException e) {
            log.error("Failed to read long-term memory", e);
        }
        
        return profile;
    }
    
    /**
     * 更新会话上下文
     */
    private void updateSessionContext(WriteMemoryRequest request) {
        String sessionId = request.getSessionId();
        // 防御 null sessionId
        if (sessionId == null) {
            sessionId = "default";
        }
        String now = LocalDateTime.now().format(FORMATTER);
        
        ReadMemoryResponse.SessionContext context = sessionContext.computeIfAbsent(sessionId, k -> 
            ReadMemoryResponse.SessionContext.builder()
                .currentTask("unknown")
                .pendingConfirmation(false)
                .lastActivity(now)
                .build()
        );
        
        context.setLastActivity(now);
        
        // 根据 action 推断当前任务
        if (request.getAction() != null) {
            if (request.getAction().contains("Resource")) {
                context.setCurrentTask("env_management");
                context.setCurrentEnv(request.getTarget());
            } else if (request.getAction().contains("Test") || request.getAction().contains("Batch")) {
                context.setCurrentTask("test_execution");
                context.setCurrentTest(request.getTarget());
            }
        }
    }
    
    /**
     * 构建记忆数据
     */
    private Map<String, Object> buildMemoryData(List<MemoryEntry> shortTerm, Map<String, Object> longTerm) {
        Map<String, Object> data = new HashMap<>();
        
        // 近期操作
        data.put("recentOperations", shortTerm.stream()
            .map(entry -> {
                Map<String, String> map = new HashMap<>();
                map.put("action", entry.getAction());
                map.put("target", entry.getTarget());
                map.put("result", entry.getResult());
                map.put("timestamp", entry.getTimestamp());
                return map;
            })
            .collect(Collectors.toList()));
        
        // 用户偏好（从长期记忆推断）
        data.put("userPreference", longTerm);
        
        return data;
    }
    
    /**
     * 生成记忆ID
     */
    private String generateMemoryId() {
        return "mem-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
