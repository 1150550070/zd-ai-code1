package com.sht.zdaicode.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class DatabaseDesignAiServiceFactory {
    @Resource
    @Qualifier("chatModelPrototype")
    private ChatModel chatModel;

    public DatabaseDesignAiService createService() {
        return AiServices.builder(DatabaseDesignAiService.class)
                .chatModel(chatModel)
                .build();
    }
}
