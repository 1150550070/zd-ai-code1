package com.sht.zdaicode.langgraph4j.node;

import cn.hutool.json.JSONUtil;
import com.sht.zdaicode.ai.VueProjectAiService;
import com.sht.zdaicode.ai.VueProjectAiServiceFactory;
import com.sht.zdaicode.ai.model.message.AiResponseMessage;
import com.sht.zdaicode.ai.model.message.ToolExecutedMessage;
import com.sht.zdaicode.ai.model.message.ToolRequestMessage;
import com.sht.zdaicode.ai.model.scheam.FrontendSchemaView;
import com.sht.zdaicode.utils.SpringContextUtil;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 分批前端代码生成器 — 将大型 Vue 全栈前端生成拆分为多个独立的小 AI 调用
 * <p>
 * 生成分为 3 个阶段：
 * <ol>
 *   <li><b>骨架阶段</b>: 生成项目基础文件 (package.json, vite.config.ts, main.ts, App.vue, request.ts)</li>
 *   <li><b>实体阶段</b>: 逐个实体生成 API 文件和增删改查页面 (src/api/xxx.ts + src/views/xxx/Index.vue)</li>
 *   <li><b>路由阶段</b>: 根据所有实体生成路由配置 (src/router/index.ts)</li>
 * </ol>
 * <b>优势</b>:
 * <ul>
 *   <li>每次 AI 调用的输入极小 (~2-3K token)，消除幻觉</li>
 *   <li>单个实体生成失败不影响其他实体</li>
 *   <li>总生成时间从 "一次超长调用 3-5 分钟" 变为 "多次短调用 1-2 分钟"</li>
 * </ul>
 * </p>
 */
@Slf4j
public class ChunkedFrontendGenerator {

    private final FrontendSchemaView schema;
    private final Long appId;
    private final Consumer<String> tokenEmitter;

    public ChunkedFrontendGenerator(FrontendSchemaView schema, Long appId, Consumer<String> tokenEmitter) {
        this.schema = schema;
        this.appId = appId;
        this.tokenEmitter = tokenEmitter;
    }

    /**
     * 执行完整的分批生成流程
     *
     * @return CompletableFuture，在所有阶段完成后 resolve
     */
    public CompletableFuture<Void> execute() {
        int totalEntities = schema.getEntities() != null ? schema.getEntities().size() : 0;
        int totalPhases = totalEntities + 2; // scaffold + N entities + router
        AtomicInteger completedPhases = new AtomicInteger(0);

        log.info("开始分批前端生成: {} 个实体, 共 {} 个阶段", totalEntities, totalPhases);
        emitProgress(String.format("\n\n> 📦 **分批生成策略启动** — 共 %d 个阶段 (1 骨架 + %d 实体 + 1 路由)\n\n",
                totalPhases, totalEntities));

        // Phase 1: Scaffold
        return executeScaffoldPhase()
                .thenAccept(v -> {
                    int done = completedPhases.incrementAndGet();
                    emitProgress(String.format("\n> ✅ [%d/%d] 项目骨架生成完成\n\n", done, totalPhases));
                })
                // Phase 2: Per-entity (sequential)
                .thenCompose(v -> executeEntityPhasesSequentially(completedPhases, totalPhases))
                // Phase 3: Router
                .thenCompose(v -> {
                    emitProgress("\n> 🛤️ 正在生成路由配置...\n\n");
                    return executeRouterPhase();
                })
                .thenAccept(v -> {
                    int done = completedPhases.incrementAndGet();
                    emitProgress(String.format("\n> ✅ [%d/%d] 路由配置生成完成\n\n", done, totalPhases));
                    log.info("分批前端生成全部完成: {} 个阶段", totalPhases);
                });
    }

    // ========== Phase 1: Scaffold ==========

    private CompletableFuture<Void> executeScaffoldPhase() {
        emitProgress("\n> 🏗️ [阶段1] 正在生成 Vue3 项目骨架文件...\n\n");

        String scaffoldPrompt = buildScaffoldPrompt();
        return executeChunk(scaffoldPrompt, "骨架文件");
    }

    private String buildScaffoldPrompt() {
        String projectName = schema.getProjectName() != null ? schema.getProjectName() : "fullstack-app";
        String projectDesc = schema.getDescription() != null ? schema.getDescription() : "全栈管理系统";

        // 构建菜单项列表，让骨架知道有哪些实体 (用于 App.vue 侧边栏)
        String menuItems = "";
        if (schema.getEntities() != null && !schema.getEntities().isEmpty()) {
            menuItems = schema.getEntities().stream()
                    .map(e -> String.format("  - 路径: /%s, 标签: %s",
                            toLowerCamel(e.getEntityName()), e.getLabel()))
                    .collect(Collectors.joining("\n"));
        }

        return String.format(
                "请只生成 Vue3 项目的基础骨架文件，不要生成任何业务页面或 API 文件。\n\n" +
                "项目名称: %s\n" +
                "项目描述: %s\n\n" +
                "需要生成的文件清单 (共5个):\n" +
                "1. package.json — 包含依赖: vue@3, vue-router@4, pinia, element-plus, axios, typescript\n" +
                "2. vite.config.ts — base: './', 配置代理 /api -> http://localhost:8080\n" +
                "3. src/main.ts — 引入 Element Plus 和路由\n" +
                "4. src/App.vue — 带 el-menu 侧边栏的管理后台布局，包含以下菜单项:\n%s\n" +
                "5. src/utils/request.ts — Axios 封装，请求/响应拦截器\n\n" +
                "⚠️ 严格要求：只生成以上 5 个文件，不要生成 router、api、views 等业务文件。\n" +
                "请直接调用 writeFile 工具写入文件。",
                projectName, projectDesc, menuItems
        );
    }

    // ========== Phase 2: Per-Entity ==========

    private CompletableFuture<Void> executeEntityPhasesSequentially(AtomicInteger completedPhases, int totalPhases) {
        List<FrontendSchemaView.EntityView> entities = schema.getEntities();
        if (entities == null || entities.isEmpty()) {
            log.info("无实体需要生成，跳过实体阶段");
            return CompletableFuture.completedFuture(null);
        }

        // 使用 CompletableFuture 链式顺序执行
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (int i = 0; i < entities.size(); i++) {
            final FrontendSchemaView.EntityView entity = entities.get(i);
            final int entityIndex = i + 1;

            chain = chain.thenCompose(v -> {
                emitProgress(String.format("\n> 💻 [实体 %d/%d] 正在生成 %s (%s) 的前端代码...\n\n",
                        entityIndex, entities.size(), entity.getEntityName(), entity.getLabel()));

                String entityPrompt = buildEntityPrompt(entity);
                return executeChunk(entityPrompt, entity.getEntityName());
            }).thenAccept(v -> {
                int done = completedPhases.incrementAndGet();
                emitProgress(String.format("\n> ✅ [%d/%d] %s 前端代码生成完成\n\n",
                        done, totalPhases, entity.getEntityName()));
            });
        }

        return chain;
    }

    private String buildEntityPrompt(FrontendSchemaView.EntityView entity) {
        String entityNameLower = toLowerCamel(entity.getEntityName());

        // 构建字段列表描述
        String fieldsDesc = "";
        if (entity.getFields() != null && !entity.getFields().isEmpty()) {
            fieldsDesc = entity.getFields().stream()
                    .map(f -> String.format("  - %s (%s, 类型: %s, %s)",
                            f.getName(), f.getLabel(), f.getType(),
                            Boolean.TRUE.equals(f.getRequired()) ? "必填" : "非必填"))
                    .collect(Collectors.joining("\n"));
        }

        return String.format(
                "请只为 \"%s\" 实体生成以下 2 个文件:\n\n" +
                "实体信息:\n" +
                "- 实体名: %s\n" +
                "- 中文名: %s\n" +
                "- API 基路径: %s\n" +
                "- 主键: %s\n" +
                "- 字段列表:\n%s\n\n" +
                "需要生成的文件:\n" +
                "1. src/api/%s.ts — 封装 CRUD 接口:\n" +
                "   - 分页查询: GET %s/page (参数: current, size, 及各条件字段)\n" +
                "   - 详情查询: GET %s/get/{id}\n" +
                "   - 新增: POST %s/add\n" +
                "   - 更新: PUT %s/update\n" +
                "   - 删除: DELETE %s/delete/{id}\n" +
                "   使用 '@/utils/request' 中导出的 axios 实例\n\n" +
                "2. src/views/%s/Index.vue — 增删改查页面:\n" +
                "   - 搜索表单 (根据字段类型选择合适的表单组件)\n" +
                "   - 数据表格 (el-table，列头使用字段的中文名)\n" +
                "   - 分页组件 (el-pagination)\n" +
                "   - 新增/编辑弹窗 (el-dialog + el-form)\n" +
                "   - 必填字段需加 el-form 校验规则\n\n" +
                "⚠️ 严格要求：只生成以上 2 个文件，不要生成其他任何文件。\n" +
                "请直接调用 writeFile 工具写入文件。",
                entity.getLabel(),
                entity.getEntityName(),
                entity.getLabel(),
                entity.getApiBasePath(),
                entity.getPrimaryKey(),
                fieldsDesc,
                entityNameLower,
                entity.getApiBasePath(), entity.getApiBasePath(),
                entity.getApiBasePath(), entity.getApiBasePath(),
                entity.getApiBasePath(),
                entityNameLower
        );
    }

    // ========== Phase 3: Router ==========

    private CompletableFuture<Void> executeRouterPhase() {
        String routerPrompt = buildRouterPrompt();
        return executeChunk(routerPrompt, "路由配置");
    }

    private String buildRouterPrompt() {
        String routeEntries = "";
        if (schema.getEntities() != null && !schema.getEntities().isEmpty()) {
            routeEntries = schema.getEntities().stream()
                    .map(e -> String.format("  - path: '/%s', name: '%s', component: () => import('@/views/%s/Index.vue'), meta: { title: '%s' }",
                            toLowerCamel(e.getEntityName()),
                            e.getEntityName(),
                            toLowerCamel(e.getEntityName()),
                            e.getLabel()))
                    .collect(Collectors.joining("\n"));
        }

        return String.format(
                "请生成路由配置文件 src/router/index.ts。\n\n" +
                "要求:\n" +
                "1. 使用 createWebHashHistory() 模式 (绝对不能使用 WebHistory)\n" +
                "2. 默认路由 '/' 重定向到第一个页面\n" +
                "3. 包含以下页面路由:\n%s\n\n" +
                "⚠️ 严格要求：只生成 src/router/index.ts 这一个文件。\n" +
                "请直接调用 writeFile 工具写入文件。",
                routeEntries
        );
    }

    // ========== Core: Execute Single Chunk ==========

    /**
     * 执行单个 chunk 的 AI 调用
     * <p>
     * 每次调用都创建全新的 VueProjectAiService 实例 (无状态、无历史)，
     * 确保每个 chunk 的上下文完全隔离。
     * </p>
     */
    private CompletableFuture<Void> executeChunk(String prompt, String chunkName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            // 每个 chunk 使用独立的 AI 服务实例
            VueProjectAiServiceFactory factory = SpringContextUtil.getBean(VueProjectAiServiceFactory.class);
            VueProjectAiService aiService = factory.createChunkedVueProjectAiService(appId);

            log.info("执行分批生成 chunk [{}], prompt 长度: {} 字符", chunkName, prompt.length());

            TokenStream tokenStream = aiService.createFullStackVueProjectCodeStream(appId, prompt);

            tokenStream
                    .onPartialResponse(partialResponse -> {
                        if (tokenEmitter != null) {
                            AiResponseMessage msg = new AiResponseMessage(partialResponse);
                            tokenEmitter.accept(JSONUtil.toJsonStr(msg));
                        }
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        if (tokenEmitter != null) {
                            ToolRequestMessage msg = new ToolRequestMessage(toolExecutionRequest);
                            tokenEmitter.accept(JSONUtil.toJsonStr(msg));
                        }
                    })
                    .onToolExecuted(toolExecution -> {
                        if (tokenEmitter != null) {
                            ToolExecutedMessage msg = new ToolExecutedMessage(toolExecution);
                            tokenEmitter.accept(JSONUtil.toJsonStr(msg));
                        }
                    })
                    .onCompleteResponse(response -> {
                        log.info("✅ 分批生成 chunk [{}] 完成", chunkName);
                        // 注意: 这里不触发 vueProjectBuilder，由 FrontendCodeGeneratorNode 在所有 chunk 完成后统一触发
                        future.complete(null);
                    })
                    .onError(error -> {
                        log.error("❌ 分批生成 chunk [{}] 失败: {}", chunkName, error.getMessage(), error);
                        emitProgress(String.format("\n> ⚠️ %s 生成遇到问题: %s\n\n", chunkName, error.getMessage()));
                        future.completeExceptionally(error);
                    })
                    .start();

        } catch (Exception e) {
            log.error("分批生成 chunk [{}] 启动异常: {}", chunkName, e.getMessage(), e);
            future.completeExceptionally(e);
        }

        return future;
    }

    // ========== Utility ==========

    private void emitProgress(String message) {
        if (tokenEmitter != null) {
            tokenEmitter.accept(message);
        }
    }

    /**
     * PascalCase → camelCase: UserInfo → userInfo
     */
    private static String toLowerCamel(String pascalCase) {
        if (pascalCase == null || pascalCase.isEmpty()) return "unknown";
        return pascalCase.substring(0, 1).toLowerCase() + pascalCase.substring(1);
    }
}
