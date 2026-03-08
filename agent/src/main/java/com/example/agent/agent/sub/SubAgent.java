package com.example.agent.agent.sub;

import com.example.agent.model.AgentRequest;
import com.example.agent.model.ExecutionResult;
import com.example.agent.model.IntentParseResult;
import reactor.core.publisher.Mono;

/**
 * 子智能体接口
 */
public interface SubAgent {
    
    /**
     * 获取智能体名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * 解析用户意图
     */
    Mono<IntentParseResult> parseIntent(AgentRequest request);
    
    /**
     * 执行任务
     */
    Mono<ExecutionResult> execute(AgentRequest request);
}
