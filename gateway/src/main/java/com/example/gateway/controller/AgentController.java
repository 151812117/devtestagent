package com.example.gateway.controller;

import com.example.gateway.model.AgentResponse;
import com.example.gateway.service.AgentService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 智能体控制器（网关层）
 * 处理用户请求的多轮交互
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 第一轮交互：意图解析
     * POST /api/agent/intent
     */
    @PostMapping("/intent")
    public Mono<ResponseEntity<AgentResponse>> parseIntent(@RequestBody IntentRequest request) {
        log.info("[Gateway] Intent parse request: {}", request.getQuery());

        String sessionId = request.getSessionId() != null ? 
            request.getSessionId() : agentService.generateSessionId();

        return agentService.processFirstRound(
                request.getQuery(),
                request.getUserId(),
                sessionId
            )
            .map(ResponseEntity::ok)
            .onErrorResume(error -> {
                log.error("[Gateway] Intent parse error: {}", error.getMessage());
                return Mono.just(ResponseEntity.ok(AgentResponse.builder()
                    .type(AgentResponse.ResponseType.ERROR)
                    .errorMessage(error.getMessage())
                    .build()));
            });
    }

    /**
     * 第二轮交互：执行确认后的任务
     * POST /api/agent/execute
     */
    @PostMapping("/execute")
    public Mono<ResponseEntity<AgentResponse>> executeTask(@RequestBody ExecuteRequest request) {
        log.info("[Gateway] Execute request: {}", request.getRequestId());

        return agentService.processSecondRound(
                request.getRequestId(),
                request.getQuery(),
                request.getParameters(),
                request.getUserId(),
                request.getSessionId()
            )
            .map(ResponseEntity::ok)
            .onErrorResume(error -> {
                log.error("[Gateway] Execute error: {}", error.getMessage());
                return Mono.just(ResponseEntity.ok(AgentResponse.builder()
                    .type(AgentResponse.ResponseType.ERROR)
                    .errorMessage(error.getMessage())
                    .build()));
            });
    }

    /**
     * 获取缓存的意图解析结果
     * GET /api/agent/intent/{requestId}
     */
    @GetMapping("/intent/{requestId}")
    public ResponseEntity<AgentResponse.IntentParseResult> getIntentResult(@PathVariable String requestId) {
        AgentResponse.IntentParseResult result = agentService.getCachedIntent(requestId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 健康检查
     * GET /api/agent/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Gateway is running");
    }

    // ============ 请求DTO ============

    @Data
    public static class IntentRequest {
        private String query;
        private String userId;
        private String sessionId;
    }

    @Data
    public static class ExecuteRequest {
        private String requestId;
        private String query;
        private String userId;
        private String sessionId;
        private Map<String, Object> parameters;
    }
}
