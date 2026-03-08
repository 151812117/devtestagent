package com.example.agent.controller;

import com.example.agent.model.ChatRequest;
import com.example.agent.model.ChatResponse;
import com.example.agent.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Chat 接口控制器
 * 提供给网关应用调用
 */
@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Chat 接口 - 处理意图解析和执行
     * POST /api/chat
     */
    @PostMapping("/chat")
    public Mono<ResponseEntity<ChatResponse>> chat(@RequestBody ChatRequest request) {
        log.info("[Agent] Chat request - phase: {}, userId: {}, requestId: {}", 
            request.getPhase(), request.getUserId(), request.getRequestId());
        
        return chatService.process(request)
            .map(ResponseEntity::ok)
            .onErrorResume(error -> {
                log.error("[Agent] Chat error: {}", error.getMessage());
                return Mono.just(ResponseEntity.ok(ChatResponse.builder()
                    .requestId(request.getRequestId())
                    .type("ERROR")
                    .errorMessage(error.getMessage())
                    .build()));
            });
    }

    /**
     * 健康检查
     * GET /api/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Agent service is running");
    }
}
