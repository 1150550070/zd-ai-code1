package com.sht.zdaicode.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sht.zdaicode.ai.guardrail.PromptSafetyInputGuardrail;
import com.sht.zdaicode.ai.tools.*;
import com.sht.zdaicode.exception.BusinessException;
import com.sht.zdaicode.exception.ErrorCode;
import com.sht.zdaicode.model.enums.CodeGenTypeEnum;
import com.sht.zdaicode.model.enums.VueProjectScenarioEnum;
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
 * Vue项目专用AI服务工厂
 * 根据场景（创建/修改）提供不同的工具集和配置
 */
@Slf4j
@Component
public class VueProjectAiServiceFactory {

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
    private final Cache<String, VueProjectAiService> serviceCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("Vue项目AI服务实例被移除，缓存键: {}, 原因: {}", key, cause);
            })
            .build();

    /**
     * 根据应用ID和场景获取Vue项目AI服务
     *
     * @param appId 应用ID
     * @param scenario 场景（创建/修改）
     * @return Vue项目AI服务实例
     */
    public VueProjectAiService getVueProjectAiService(long appId, CodeGenTypeEnum scenario) {
        String cacheKey = buildCacheKey(appId, scenario);
        return serviceCache.get(cacheKey, key -> createVueProjectAiService(appId, scenario, null));
    }

    /**
     * 根据应用ID、场景和用户消息获取Vue项目AI服务（智能工具选择）
     *
     * @param appId 应用ID
     * @param scenario 场景（创建/修改）
     * @param userMessage 用户消息（用于智能工具选择）
     * @return Vue项目AI服务实例
     */
    public VueProjectAiService getVueProjectAiServiceWithSmartTools(long appId, CodeGenTypeEnum scenario, String userMessage) {
        // 对于智能工具选择，不使用缓存，每次都重新创建以确保工具集的准确性
        return createVueProjectAiService(appId, scenario, userMessage);
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum scenario) {
        return "vue_" + appId + "_" + scenario.getValue();
    }

    /**
     * 创建Vue项目AI服务实例
     *
     * @param appId 应用ID
     * @param scenario 场景
     * @param userMessage 用户消息（可选，用于智能工具选择）
     * @return Vue项目AI服务实例
     */
    private VueProjectAiService createVueProjectAiService(long appId, CodeGenTypeEnum scenario, String userMessage) {
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
            // 使用智能工具选择器
            tools = smartToolSelector.selectOptimalTools(scenario, appId, userMessage);
            log.info("为应用 {} 创建 {} 模式的Vue项目AI服务（智能选择），工具数量: {}", appId, scenario.getText(), tools.size());
        } else {
            // 使用传统工具选择
            tools = getToolsByScenario(scenario);
            log.info("为应用 {} 创建 {} 模式的Vue项目AI服务（传统选择），工具数量: {}", appId, scenario.getText(), tools.size());
        }

        StreamingChatModel selectedModel = scenario == CodeGenTypeEnum.VUE_PROJECT_EDIT
                ? SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class)
                : SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
        return AiServices.builder(VueProjectAiService.class)
                .streamingChatModel(selectedModel)
                .chatMemoryProvider(memoryId -> chatMemory)
                .tools(tools)
                .inputGuardrails(new PromptSafetyInputGuardrail())
//                .outputGuardrails(new RetryOutputGuardrail())
                .build();
    }

    /**
     * 根据场景获取对应的工具集（传统模式）
     *
     * @param scenario 场景
     * @return 工具列表
     */
    private List<Object> getToolsByScenario(CodeGenTypeEnum scenario) {
        return switch (scenario) {
            case VUE_PROJECT_CREATE -> {
                // 创建模式：基础文件写入工具
                log.debug("创建模式工具集（传统）：文件写入工具");
                yield List.of(
                        toolManager.getTool("writeFile")
                );
            }
            case VUE_PROJECT_EDIT -> {
                // 修改模式：基础读取和修改工具
                log.debug("修改模式工具集（传统）：文件读取、修改工具");
                yield List.of(
                        toolManager.getTool("readFile"),
                        toolManager.getTool("modifyFile")
                );
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的Vue项目场景: " + scenario.getValue());
        };
    }

    /**
     * 获取指定场景的工具列表（用于测试和调试）
     *
     * @param scenario 场景
     * @param appId 应用ID（用于日志）
     * @return 工具列表
     */
    public List<Object> getToolsForScenario(CodeGenTypeEnum scenario, Long appId) {
        List<Object> tools = getToolsByScenario(scenario);
        log.info("应用 {} 的 {} 场景工具列表: {}", appId, scenario.getText(), 
            tools.stream().map(tool -> tool.getClass().getSimpleName()).toList());
        return tools;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        serviceCache.invalidateAll();
        log.info("Vue项目AI服务缓存已清除");
    }
}
