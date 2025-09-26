package com.sht.zdaicode.core.handler;

import com.sht.zdaicode.model.enums.ChatHistoryMessageTypeEnum;
import com.sht.zdaicode.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Agent模式流处理器
 * 专门处理Agent模式的流式输出，避免数据库字段长度问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentModeStreamHandler {

    private final ChatHistoryService chatHistoryService;
    private final StructuredAgentModeStreamHandler structuredHandler;

    /**
     * 处理Agent模式的流式输出
     * @param sourceStream 源数据流
     * @param appId 应用ID
     * @param userMessage 用户消息
     * @param userId 用户ID
     * @param structured 是否使用结构化输出
     */
    public Flux<String> handleAgentStream(Flux<String> sourceStream, Long appId, String userMessage, Long userId, boolean structured) {
        if (structured) {
            return structuredHandler.handleStructuredAgentStream(sourceStream, appId, userMessage, userId);
        }
        return handleTraditionalAgentStream(sourceStream, appId, userMessage, userId);
    }

    /**
     * 处理Agent模式的流式输出（兼容旧版本）
     */
    public Flux<String> handleAgentStream(Flux<String> sourceStream, Long appId, String userMessage, Long userId) {
        return handleTraditionalAgentStream(sourceStream, appId, userMessage, userId);
    }

    /**
     * 传统的Agent模式流处理
     */
    private Flux<String> handleTraditionalAgentStream(Flux<String> sourceStream, Long appId, String userMessage, Long userId) {
        StringBuilder summaryBuilder = new StringBuilder();
        
        return sourceStream
                .map(chunk -> {
                    // 格式化输出，使其更结构化
                    String formattedChunk = formatChunkForDisplay(chunk);
                    
                    // 收集关键信息用于数据库存储
                    if (chunk.contains("**步骤") || chunk.contains("**开始执行") || chunk.contains("**代码生成完成")) {
                        summaryBuilder.append(extractKeyInfo(chunk));
                    }
                    
                    return formattedChunk;
                })
                .doOnComplete(() -> {
                    try {
                        // 保存用户消息
                        chatHistoryService.addChatMessage(appId, userMessage, ChatHistoryMessageTypeEnum.USER.getValue(), userId);
                        
                        // 保存简化的助手响应摘要
                        String summary = summaryBuilder.toString();
                        if (summary.length() > 1000) {
                            summary = summary.substring(0, 1000) + "...";
                        }
                        
                        if (summary.isEmpty()) {
                            summary = "Agent模式代码生成完成";
                        }
                        
                        chatHistoryService.addChatMessage(appId, summary, ChatHistoryMessageTypeEnum.AI.getValue(), userId);
                        log.info("Agent模式对话历史保存成功，应用ID: {}, 用户ID: {}", appId, userId);
                        
                    } catch (Exception e) {
                        log.error("保存Agent模式对话历史失败", e);
                    }
                })
                .doOnError(error -> {
                    try {
                        // 保存用户消息
                        chatHistoryService.addChatMessage(appId, userMessage, ChatHistoryMessageTypeEnum.USER.getValue(), userId);
                        // 保存错误信息
                        chatHistoryService.addChatMessage(appId, "Agent模式执行失败: " + error.getMessage(), ChatHistoryMessageTypeEnum.AI.getValue(), userId);
                    } catch (Exception e) {
                        log.error("保存Agent模式错误信息失败", e);
                    }
                });
    }

    /**
     * 格式化输出块，使其更结构化和易读
     */
    private String formatChunkForDisplay(String chunk) {
        if (chunk == null || chunk.trim().isEmpty()) {
            return chunk;
        }
        
        // 检测是否是步骤开始
        if (chunk.contains("**步骤") || chunk.contains("**开始执行")) {
            String separator = "=".repeat(50);
            return "\n" + separator + "\n" + chunk + "\n" + separator + "\n";
        }
        
        // 检测是否是重要状态信息
        if (chunk.contains("✅") || chunk.contains("❌") || chunk.contains("完成")) {
            return "\n📋 " + chunk + "\n";
        }
        
        // 检测是否是代码生成完成
        if (chunk.contains("**代码生成完成")) {
            String separator = "-".repeat(30);
            return "\n🎉 " + chunk + "\n" + separator + "\n";
        }
        
        return chunk;
    }

    /**
     * 提取关键信息用于数据库存储
     */
    private String extractKeyInfo(String chunk) {
        // 移除markdown格式和emoji，只保留核心信息
        String cleaned = chunk.replaceAll("[🚀💭✅❌🔧🎨📸🖼️📊🏷️🔗✨🛤️💻🔍🏗️⚙️🌐🎭📈🎯🔄⚡🔬🔨]", "")
                              .replaceAll("\\*\\*", "")
                              .replaceAll("\\n+", " ")
                              .trim();
        
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100) + "...";
        }
        
        return cleaned + " ";
    }
}