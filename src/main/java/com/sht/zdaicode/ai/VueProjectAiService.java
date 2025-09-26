package com.sht.zdaicode.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * Vue 项目专用 AI 服务
 * 区分创建和修改两种模式，使用不同的提示词和工具集
 */
public interface VueProjectAiService {

    /**
     * 创建 Vue 项目代码（流式）
     * 使用创建模式的提示词和工具集
     *
     * @param appId 应用ID
     * @param userMessage 用户消息
     * @return 生成过程的流式响应
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-create-system-prompt.txt")
    TokenStream createVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);

    /**
     * 修改 Vue 项目代码（流式）
     * 使用修改模式的提示词和工具集
     *
     * @param appId 应用ID
     * @param userMessage 用户消息
     * @return 生成过程的流式响应
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-edit-system-prompt.txt")
    TokenStream editVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);
}