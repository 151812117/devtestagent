package com.example.agent.service;

import com.example.agent.agent.master.MasterAgent;
import com.example.agent.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Chat 服务
 * 处理网关发送的 chat 请求，协调 MasterAgent 完成任务
 */
@Slf4j
@Service
public class ChatService {

    private final MasterAgent masterAgent;

    public ChatService(MasterAgent masterAgent) {
        this.masterAgent = masterAgent;
    }

    /**
     * 处理 chat 请求
     */
    public Mono<ChatResponse> process(ChatRequest request) {
        String phase = request.getPhase();
        
        if ("INTENT_PARSE".equals(phase)) {
            return processIntentParse(request);
        } else if ("EXECUTION".equals(phase)) {
            return processExecution(request);
        } else {
            return Mono.error(new IllegalArgumentException("Unknown phase: " + phase));
        }
    }

    /**
     * 处理意图解析阶段
     */
    private Mono<ChatResponse> processIntentParse(ChatRequest request) {
        String requestId = request.getRequestId() != null ? 
            request.getRequestId() : generateRequestId();
        
        // 构建 AgentRequest
        AgentRequest agentRequest = AgentRequest.builder()
            .requestId(requestId)
            .userQuery(request.getContent())
            .userId(request.getUserId())
            .sessionId(request.getSessionId())
            .phase(AgentRequest.RequestPhase.INTENT_PARSE)
            .memory(request.getMemoryContent())
            .build();

        // 调用 MasterAgent 进行意图解析
        return masterAgent.processIntentParse(agentRequest)
            .map(intentResult -> ChatResponse.builder()
                .responseId(generateRequestId())
                .requestId(requestId)
                .type("INTENT_PARSE_RESULT")
                .intentResult(convertToChatIntentResult(intentResult))
                .build())
            .onErrorResume(error -> {
                log.error("[ChatService] Intent parse error: {}", error.getMessage());
                return Mono.just(ChatResponse.builder()
                    .responseId(generateRequestId())
                    .requestId(requestId)
                    .type("ERROR")
                    .errorMessage(error.getMessage())
                    .build());
            });
    }

    /**
     * 处理执行阶段
     */
    private Mono<ChatResponse> processExecution(ChatRequest request) {
        String requestId = request.getRequestId();
        Map<String, Object> context = request.getContext();
        
        if (context == null || !context.containsKey("action")) {
            return Mono.error(new IllegalArgumentException("Execution context must contain action"));
        }

        String action = context.get("action").toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) context.getOrDefault("parameters", 
            new java.util.HashMap<>());

        // 构建 AgentRequest
        AgentRequest agentRequest = AgentRequest.builder()
            .requestId(requestId)
            .userQuery(request.getOriginalQuery())
            .userId(request.getUserId())
            .sessionId(request.getSessionId())
            .phase(AgentRequest.RequestPhase.EXECUTION)
            .parameters(parameters)
            .build();

        // 调用 MasterAgent 执行任务
        return masterAgent.processExecution(action, agentRequest)
            .map(executionResult -> ChatResponse.builder()
                .responseId(generateRequestId())
                .requestId(requestId)
                .type("EXECUTION_RESULT")
                .executionResult(convertToChatExecutionResult(executionResult))
                .build())
            .onErrorResume(error -> {
                log.error("[ChatService] Execution error: {}", error.getMessage());
                return Mono.just(ChatResponse.builder()
                    .responseId(generateRequestId())
                    .requestId(requestId)
                    .type("ERROR")
                    .errorMessage(error.getMessage())
                    .build());
            });
    }

    /**
     * 转换意图解析结果
     */
    private ChatResponse.IntentParseResult convertToChatIntentResult(IntentParseResult result) {
        return ChatResponse.IntentParseResult.builder()
            .action(result.getAction())
            .target(result.getTarget())
            .parameters(result.getParameters())
            .think(result.getThink())
            .missingParameters(result.getMissingParameters())
            .confirmationMessage(result.getConfirmationMessage())
            .build();
    }

    /**
     * 转换执行结果
     */
    private ChatResponse.ExecutionResult convertToChatExecutionResult(ExecutionResult result) {
        return ChatResponse.ExecutionResult.builder()
            .success(result.isSuccess())
            .message(result.getMessage())
            .data(result.getData())
            .build();
    }

    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
