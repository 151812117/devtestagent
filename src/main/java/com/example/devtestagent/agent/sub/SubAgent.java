package com.example.devtestagent.agent.sub;

import com.example.devtestagent.model.AgentRequest;
import com.example.devtestagent.model.AgentResponse;
import com.example.devtestagent.model.IntentParseResult;
import reactor.core.publisher.Mono;

/**
 * 子智能体接口
 * 所有子智能体都需要实现此接口
 */
public interface SubAgent {

    /**
     * 获取智能体名称
     */
    String getName();

    /**
     * 解析用户意图
     * 第一阶段：解析用户输入，提取参数，返回 JSON 格式结果
     */
    Mono<IntentParseResult> parseIntent(AgentRequest request);

    /**
     * 执行具体任务
     * 第二阶段：根据确认后的参数执行任务
     */
    Mono<AgentResponse.ExecutionResult> execute(AgentRequest request);
}
