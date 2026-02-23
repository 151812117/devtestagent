package com.example.mcpserver.service;

import com.example.mcpserver.model.*;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 环境管理服务
 */
@Slf4j
@Component
public class EnvService {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 申请环境资源
     */
    public CommonResponse<ApplyResourceResponse> applyResource(ApplyResourceRequest request) {
        log.info("Applying resource: envType={}, owner={}", request.getEnvType(), request.getOwner());
        
        // 模拟申请环境资源
        String envId = generateEnvId();
        String envName = buildEnvName(request.getEnvType(), request.getOwner());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusHours(request.getDuration() != null ? request.getDuration() : 24);
        
        ApplyResourceResponse response = ApplyResourceResponse.builder()
            .envId(envId)
            .envName(envName)
            .status("RUNNING")
            .createTime(now.format(FORMATTER))
            .expireTime(expireTime.format(FORMATTER))
            .accessUrl("https://" + envId + ".example.com")
            .build();
        
        log.info("Resource applied successfully: envId={}", envId);
        return CommonResponse.ok("Environment resource applied successfully", response);
    }
    
    /**
     * 回收环境资源
     */
    public CommonResponse<RecycleResourceResponse> recycleResource(RecycleResourceRequest request) {
        log.info("Recycling resource: envId={}, recycleType={}", request.getEnvId(), request.getRecycleType());
        
        // 模拟回收环境资源
        String recycleId = generateRecycleId();
        LocalDateTime now = LocalDateTime.now();
        
        RecycleResourceResponse response = RecycleResourceResponse.builder()
            .recycleId(recycleId)
            .envId(request.getEnvId())
            .status("RECYCLED")
            .recycleTime(now.format(FORMATTER))
            .releasedResources("CPU, Memory, Storage")
            .build();
        
        log.info("Resource recycled successfully: recycleId={}", recycleId);
        return CommonResponse.ok("Environment resource recycled successfully", response);
    }
    
    /**
     * 生成环境ID
     */
    private String generateEnvId() {
        return "env-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
    
    /**
     * 生成回收ID
     */
    private String generateRecycleId() {
        return "rec-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
    
    /**
     * 构建环境名称
     */
    private String buildEnvName(String envType, String owner) {
        String typeStr = envType != null ? envType : "dev";
        String ownerStr = owner != null ? owner : "anonymous";
        return typeStr + "-" + ownerStr + "-" + System.currentTimeMillis() % 10000;
    }
}
