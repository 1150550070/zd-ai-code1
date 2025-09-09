package com.sht.zdaicode.core.handler;

import com.sht.zdaicode.model.entity.User;
import com.sht.zdaicode.model.enums.ChatHistoryMessageTypeEnum;
import com.sht.zdaicode.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 简单的文本流处理器
 * 1. 实时收集AI响应的内容
 * 2. 当AI响应完成时,将响应内容保存到对话历史中
 * 3. 当AI响应失败时,将错误信息保存到对话历史中
 */
@Slf4j
public class SimpleTextStreamHandler {
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               Long appId,
                               User loginUser
    ){
        StringBuilder aiResponseBuilder = new StringBuilder();
        return originFlux.map(chunk -> {
                    //实时收集AI响应的内容
                    aiResponseBuilder.append(chunk);
                    return chunk;
                }
        ).doOnComplete(() -> {
            //流式返回完成后,保存AI消息到对话历史中
            String aiResponse = aiResponseBuilder.toString();
            chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        }).doOnError(error -> {
            // 处理错误，例如记录日志
            String errorMessage = "AI 生成代码失败：" + error.getMessage();
            chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        });
    }
}
