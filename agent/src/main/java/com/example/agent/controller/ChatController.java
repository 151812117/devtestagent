package com.example.agent.controller;

import com.example.agent.model.ChatRequest;
import com.example.agent.model.ChatResponse;
import com.example.agent.model.ChatStreamEvent;
import com.example.agent.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

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
     * Chat 接口 - 处理意图解析和执行（非流式，保持兼容）
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
     * Chat 接口 - 流式 SSE 版本
     * POST /api/chat/stream
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> chatStream(@RequestBody ChatRequest request) {
        String requestId = request.getRequestId() != null ? 
            request.getRequestId() : generateRequestId();
        
        log.info("[Agent] Chat stream request - phase: {}, userId: {}, requestId: {}", 
            request.getPhase(), request.getUserId(), requestId);

        // 发送开始事件
        ServerSentEvent<ChatStreamEvent> startEvent = ServerSentEvent.<ChatStreamEvent>builder()
            .id(generateEventId())
            .event("start")
            .data(ChatStreamEvent.builder()
                .eventType(ChatStreamEvent.EventType.START)
                .requestId(requestId)
                .data("Processing started")
                .build())
            .build();

        // 处理流
        Flux<ServerSentEvent<ChatStreamEvent>> processFlux = chatService.processStream(request, requestId)
            .map(event -> ServerSentEvent.<ChatStreamEvent>builder()
                .id(generateEventId())
                .event(event.getEventType().name().toLowerCase())
                .data(event)
                .build());

        // 发送完成事件
        ServerSentEvent<ChatStreamEvent> completeEvent = ServerSentEvent.<ChatStreamEvent>builder()
            .id(generateEventId())
            .event("complete")
            .data(ChatStreamEvent.builder()
                .eventType(ChatStreamEvent.EventType.COMPLETE)
                .requestId(requestId)
                .data("Processing completed")
                .build())
            .build();

        return Flux.concat(
            Flux.just(startEvent),
            processFlux,
            Flux.just(completeEvent)
        ).onErrorResume(error -> {
            log.error("[Agent] Chat stream error: {}", error.getMessage());
            return Flux.just(ServerSentEvent.<ChatStreamEvent>builder()
                .id(generateEventId())
                .event("error")
                .data(ChatStreamEvent.builder()
                    .eventType(ChatStreamEvent.EventType.ERROR)
                    .requestId(requestId)
                    .errorMessage(error.getMessage())
                    .build())
                .build());
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

    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String generateEventId() {
        return String.valueOf(System.currentTimeMillis());
    }
}
