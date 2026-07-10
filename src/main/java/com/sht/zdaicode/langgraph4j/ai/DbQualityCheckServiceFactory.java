package com.sht.zdaicode.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class DbQualityCheckServiceFactory {

    @Resource(name = "chatModelPrototype")
    private ChatModel chatModel;

    @Bean
    public DbQualityCheckService createDbQualityCheckService() {
        return AiServices.builder(DbQualityCheckService.class)
                .chatModel(chatModel)
                .build();
    }
}
