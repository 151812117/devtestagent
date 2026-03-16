package com.example.gateway.client;

import com.example.gateway.model.AgentResponse;
import com.example.gateway.model.ChatStreamEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 服务客户端
 * 调用 Agent 应用的 chat 接口
 */
@Slf4j
@Component
public class AgentClient {

    private final WebClient webClient;

    @Value("${agent.service.url:http://localhost:8081}")
    private String agentServiceUrl;

    public AgentClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * 调用 Agent 的 chat 接口 - 意图解析阶段
     */
    public Mono<AgentResponse> chatIntentParse(String userId, String sessionId, String requestId, 
                                                String content, String memoryContent) {
        
        // 处理可能的 null 值
        String safeUserId = userId != null ? userId : "anonymous";
        String safeSessionId = sessionId != null ? sessionId : "default";
        String safeRequestId = requestId != null ? requestId : "";
        String safeContent = content != null ? content : "";
        String safeMemory = memoryContent != null ? memoryContent : "";
        
        Map<String, Object> request = new HashMap<>();
        request.put("userId", safeUserId);
        request.put("sessionId", safeSessionId);
        request.put("requestId", safeRequestId);
        request.put("content", safeContent);
        request.put("phase", "INTENT_PARSE");
        request.put("memoryContent", safeMemory);

        return callChatApi(request);
    }

    /**
     * 调用 Agent 的 chat 接口 - 执行阶段
     */
    public Mono<AgentResponse> chatExecution(String userId, String sessionId, String requestId,
                                              String originalQuery, String action, 
                                              Map<String, Object> parameters) {
        
        // 处理可能的 null 值
        String safeUserId = userId != null ? userId : "anonymous";
        String safeSessionId = sessionId != null ? sessionId : "default";
        String safeRequestId = requestId != null ? requestId : "";
        String safeQuery = originalQuery != null ? originalQuery : "";
        String safeAction = action != null ? action : "";
        Map<String, Object> safeParams = parameters != null ? parameters : java.util.Collections.emptyMap();
        
        Map<String, Object> context = new HashMap<>();
        context.put("action", safeAction);
        context.put("parameters", safeParams);

        Map<String, Object> request = new HashMap<>();
        request.put("userId", safeUserId);
        request.put("sessionId", safeSessionId);
        request.put("requestId", safeRequestId);
        request.put("content", safeQuery);
        request.put("phase", "EXECUTION");
        request.put("originalQuery", safeQuery);
        request.put("context", context);

        return callChatApi(request);
    }

    /**
     * 调用 Agent 的 chat 流式接口 - 意图解析阶段
     */
    public Flux<ServerSentEvent<ChatStreamEvent>> chatIntentParseStream(String userId, String sessionId, 
                                                                         String requestId, String content, 
                                                                         String memoryContent) {
        // 处理可能的 null 值
        String safeUserId = userId != null ? userId : "anonymous";
        String safeSessionId = sessionId != null ? sessionId : "default";
        String safeRequestId = requestId != null ? requestId : "";
        String safeContent = content != null ? content : "";
        String safeMemory = memoryContent != null ? memoryContent : "";
        
        Map<String, Object> request = new HashMap<>();
        request.put("userId", safeUserId);
        request.put("sessionId", safeSessionId);
        request.put("requestId", safeRequestId);
        request.put("content", safeContent);
        request.put("phase", "INTENT_PARSE");
        request.put("memoryContent", safeMemory);

        return callChatStreamApi(request);
    }

    /**
     * 调用 Agent 的 chat 流式接口 - 执行阶段
     */
    public Flux<ServerSentEvent<ChatStreamEvent>> chatExecutionStream(String userId, String sessionId, 
                                                                       String requestId, String originalQuery, 
                                                                       String action, 
                                                                       Map<String, Object> parameters) {
        // 处理可能的 null 值
        String safeUserId = userId != null ? userId : "anonymous";
        String safeSessionId = sessionId != null ? sessionId : "default";
        String safeRequestId = requestId != null ? requestId : "";
        String safeQuery = originalQuery != null ? originalQuery : "";
        String safeAction = action != null ? action : "";
        Map<String, Object> safeParams = parameters != null ? parameters : java.util.Collections.emptyMap();
        
        Map<String, Object> context = new HashMap<>();
        context.put("action", safeAction);
        context.put("parameters", safeParams);

        Map<String, Object> request = new HashMap<>();
        request.put("userId", safeUserId);
        request.put("sessionId", safeSessionId);
        request.put("requestId", safeRequestId);
        request.put("content", safeQuery);
        request.put("phase", "EXECUTION");
        request.put("originalQuery", safeQuery);
        request.put("context", context);

        return callChatStreamApi(request);
    }

    private Mono<AgentResponse> callChatApi(Map<String, Object> request) {
        log.info("[AgentClient] Calling chat API: {}", request);
        
        return webClient.post()
            .uri(agentServiceUrl + "/api/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(AgentResponse.class)
            .doOnNext(response -> log.info("[AgentClient] Chat response: {}", response))
            .onErrorResume(error -> {
                log.error("[AgentClient] Chat API error: {}", error.getMessage());
                return Mono.just(AgentResponse.builder()
                    .type(AgentResponse.ResponseType.ERROR)
                    .errorMessage("Agent service error: " + error.getMessage())
                    .build());
            });
    }

    private Flux<ServerSentEvent<ChatStreamEvent>> callChatStreamApi(Map<String, Object> request) {
        log.info("[AgentClient] Calling chat stream API: {}", request);
        
        return webClient.post()
            .uri(agentServiceUrl + "/api/chat/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(new org.springframework.core.ParameterizedTypeReference<ServerSentEvent<ChatStreamEvent>>() {})
            .doOnNext(event -> log.debug("[AgentClient] Stream event: {}", event))
            .onErrorResume(error -> {
                log.error("[AgentClient] Chat stream API error: {}", error.getMessage());
                return Flux.just(ServerSentEvent.<ChatStreamEvent>builder()
                    .event("error")
                    .data(ChatStreamEvent.builder()
                        .eventType(ChatStreamEvent.EventType.ERROR)
                        .errorMessage("Agent service error: " + error.getMessage())
                        .build())
                    .build());
            });
    }
}
