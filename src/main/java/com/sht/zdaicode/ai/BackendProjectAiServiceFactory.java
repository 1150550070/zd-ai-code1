package com.sht.zdaicode.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sht.zdaicode.ai.guardrail.PromptSafetyInputGuardrail;
import com.sht.zdaicode.ai.tools.*;
import com.sht.zdaicode.exception.BusinessException;
import com.sht.zdaicode.exception.ErrorCode;
import com.sht.zdaicode.model.enums.CodeGenTypeEnum;
import com.sht.zdaicode.service.ChatHistoryService;
import com.sht.zdaicode.utils.SpringContextUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Java 后端项目专用 AI 服务工厂
 * 根据场景（创建/修改）提供不同的工具集和配置
 */
@Slf4j
@Component
public class BackendProjectAiServiceFactory {

    @Resource
    @Qualifier("reasoningStreamingChatModelPrototype")
    private StreamingChatModel reasoningStreamingChatModel;

    @Resource
    @Qualifier("streamingChatModelPrototype")
    private StreamingChatModel streamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;

    @Resource
    private SmartToolSelector smartToolSelector;

    /**
     * AI 服务实例缓存
     */
    private final Cache<String, BackendProjectAiService> serviceCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("Backend项目AI服务实例被移除，缓存键: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据应用ID和场景获取后端项目AI服务
     */
    public BackendProjectAiService getBackendProjectAiService(long appId, CodeGenTypeEnum scenario) {
        String cacheKey = buildCacheKey(appId, scenario);
        return serviceCache.get(cacheKey, key -> createBackendProjectAiService(appId, scenario, null));
    }

    /**
     * 根据应用ID、场景和用户消息获取后端项目AI服务（智能工具选择）
     */
    public BackendProjectAiService getBackendProjectAiServiceWithSmartTools(long appId, CodeGenTypeEnum scenario, String userMessage) {
        // 对于智能工具选择，不使用缓存，每次都重新创建以确保工具集的准确性
        return createBackendProjectAiService(appId, scenario, userMessage);
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum scenario) {
        return "backend_" + appId + "_" + scenario.getValue();
    }

    /**
     * 创建后端项目AI服务实例
     */
    private BackendProjectAiService createBackendProjectAiService(long appId, CodeGenTypeEnum scenario, String userMessage) {
        // 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();

        // 从数据库加载历史对话到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);

        // 根据场景和用户消息智能选择工具集
        List<Object> tools;
        if (userMessage != null && !userMessage.trim().isEmpty()) {
            tools = smartToolSelector.selectOptimalTools(scenario, appId, userMessage);
            log.info("为应用 {} 创建 {} 模式的Backend项目AI服务（智能选择），工具数量: {}", appId, scenario.getText(), tools.size());
        } else {
            tools = getToolsByScenario(scenario);
            log.info("为应用 {} 创建 {} 模式的Backend项目AI服务（传统选择），工具数量: {}", appId, scenario.getText(), tools.size());
        }

        // 暂无 Edit 模式，默认全走 BACKEND_JAVA（推理模型）
        StreamingChatModel selectedModel = scenario == CodeGenTypeEnum.valueOf("BACKEND_JAVA")
                ? SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class)
                : SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);

        return AiServices.builder(BackendProjectAiService.class)
                .streamingChatModel(selectedModel)
                .chatMemoryProvider(memoryId -> chatMemory)
                .tools(tools)
//                .inputGuardrails(new PromptSafetyInputGuardrail())
                .build();
    }

    /**
     * 根据场景获取对应的工具集（传统模式）
     */
    private List<Object> getToolsByScenario(CodeGenTypeEnum scenario) {
        if (scenario.name().equals("BACKEND_JAVA")) {
            log.debug("后端创建模式工具集（传统）：文件写入工具");
            return List.of(toolManager.getTool("writeFile"));
        } else if (scenario.name().equals("BACKEND_PROJECT_EDIT")) {
            log.debug("后端修改模式工具集（传统）：文件读取、修改工具");
            return List.of(
                    toolManager.getTool("readFile"),
                    toolManager.getTool("modifyFile")
            );
        } else {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的Backend项目场景: " + scenario.getValue());
        }
    }

    public void clearCache() {
        serviceCache.invalidateAll();
        log.info("Backend项目AI服务缓存已清除");
    }
}