package com.sht.zdaicode.core.handler;

import com.sht.zdaicode.model.enums.ChatHistoryMessageTypeEnum;
import com.sht.zdaicode.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Agent模式流处理器
 * 专门处理Agent模式的流式输出，统一使用结构化输出
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentModeStreamHandler {

    private final ChatHistoryService chatHistoryService;
    private final StructuredAgentModeStreamHandler structuredHandler;

    /**
     * 处理Agent模式的流式输出（统一使用结构化输出）
     * @param sourceStream 源数据流
     * @param appId 应用ID
     * @param userMessage 用户消息
     * @param userId 用户ID
     * @param structured 是否使用结构化输出（Agent模式强制为true）
     */
    public Flux<String> handleAgentStream(Flux<String> sourceStream, Long appId, String userMessage, Long userId, boolean structured) {
        // Agent模式统一使用结构化输出
        return structuredHandler.handleStructuredAgentStream(sourceStream, appId, userMessage, userId);
    }

    /**
     * 处理Agent模式的流式输出（兼容旧版本，自动使用结构化输出）
     */
    public Flux<String> handleAgentStream(Flux<String> sourceStream, Long appId, String userMessage, Long userId) {
        // 兼容旧版本调用，统一使用结构化输出
        return structuredHandler.handleStructuredAgentStream(sourceStream, appId, userMessage, userId);
    }
}