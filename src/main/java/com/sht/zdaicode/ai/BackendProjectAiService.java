package com.sht.zdaicode.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface BackendProjectAiService {
    /**
     * 创建 Java 后端项目代码（流式）
     * 目标技术栈: Spring Boot 3 + MyBatis-Plus + MySQL
     *
     * @param appId 应用ID
     * @param userMessage 用户消息（包含 ProjectSchema 的 JSON 和自然语言需求）
     * @return 生成过程的流式响应
     */
    @SystemMessage(fromResource = "prompt/codegen-backend-java-create-system-prompt.txt")
    TokenStream createBackendProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);

    /**
     * 修改 Java 后端项目代码（流式）
     * 用于在现有代码基础上增删改查逻辑、新增表映射等
     *
     * @param appId 应用ID
     * @param userMessage 用户消息
     * @return 生成过程的流式响应
     */
    @SystemMessage(fromResource = "prompt/codegen-backend-java-edit-system-prompt.txt")
    TokenStream editBackendProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);
}
