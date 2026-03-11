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
 * 修复版：在编辑模式下【强制禁用】SmartToolSelector，物理隔离 writeFile 工具
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

    public VueProjectAiService getVueProjectAiService(long appId, VueProjectScenarioEnum scenario) {
        String cacheKey = buildCacheKey(appId, scenario);
        return serviceCache.get(cacheKey, key -> createVueProjectAiService(appId, scenario, null));
    }

    public VueProjectAiService getVueProjectAiServiceWithSmartTools(long appId, VueProjectScenarioEnum scenario,
            String userMessage) {
        // 每次重新创建以确保工具集准确
        return createVueProjectAiService(appId, scenario, userMessage);
    }

    private String buildCacheKey(long appId, VueProjectScenarioEnum scenario) {
        return "vue_" + appId + "_" + scenario.getValue();
    }

    private VueProjectAiService createVueProjectAiService(long appId, VueProjectScenarioEnum scenario,
            String userMessage) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();

        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);

        List<Object> tools;

        // =========================================================================
        // 核心修复逻辑：优先级调整
        // 1. 如果是编辑模式 (VUE_PROJECT_EDIT)，绝对禁止使用智能选择器。
        // 必须强制使用 getToolsByScenario 返回的“只读+修改”工具集。
        // =========================================================================
        if (scenario == VueProjectScenarioEnum.VUE_PROJECT_EDIT) {
            tools = getToolsByScenario(scenario);
            log.info("应用 {} 处于编辑模式：强制使用严格工具集（禁用智能选择），工具数量: {}", appId, tools.size());
        }
        // 2. 只有在创建模式下，才允许尝试智能选择
        else if (userMessage != null && !userMessage.trim().isEmpty()) {
            try {
                tools = smartToolSelector.selectOptimalTools(scenario, appId, userMessage);
                log.info("应用 {} 处于创建模式：启用智能工具选择，工具数量: {}", appId, tools.size());
            } catch (Exception e) {
                log.warn("智能工具选择失败，回退到默认工具集", e);
                tools = getToolsByScenario(scenario);
            }
        }
        // 3. 默认回退
        else {
            tools = getToolsByScenario(scenario);
            log.info("使用默认工具集，工具数量: {}", tools.size());
        }

        // 选择模型 (Reasoning 模型常这里指向 qwen-max，适合创建满载工具；普通模型 deepseek-chat 适合小修小改)
        StreamingChatModel selectedModel = scenario == VueProjectScenarioEnum.VUE_PROJECT_EDIT
                ? SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class)
                : SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);

        return AiServices.builder(VueProjectAiService.class)
                .streamingChatModel(selectedModel)
                .chatMemoryProvider(memoryId -> chatMemory)
                .tools(tools)
                .inputGuardrails(new PromptSafetyInputGuardrail())
                .build();
    }

    /**
     * 获取严格的场景工具集
     */
    private List<Object> getToolsByScenario(VueProjectScenarioEnum scenario) {
        return switch (scenario) {
            case VUE_PROJECT_CREATE -> {
                log.debug("创建模式工具集：允许写入");
                yield List.of(toolManager.getTool("writeFile"));
            }
            case VUE_PROJECT_EDIT -> {
                log.debug("修改模式工具集：只允许读取和修改 (物理隔离 writeFile)");
                // 关键点：列表中绝对没有 writeFile
                yield List.of(
                        toolManager.getTool("readFile"),
                        toolManager.getTool("modifyFile"));
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "不支持的Vue项目场景: " + scenario.getValue());
        };
    }

    // ... 其他辅助方法保持不变 ...
    public List<Object> getToolsForScenario(VueProjectScenarioEnum scenario, Long appId) {
        return getToolsByScenario(scenario);
    }

    public void clearCache() {
        serviceCache.invalidateAll();
    }
}