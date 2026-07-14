package com.sht.zdaicode.langgraph4j.node;

import cn.hutool.json.JSONUtil;
import com.sht.zdaicode.ai.AiCodeGenTypeRoutingService;
import com.sht.zdaicode.ai.model.scheam.FrontendSchemaView;
import com.sht.zdaicode.ai.model.scheam.ProjectScheam;
import com.sht.zdaicode.ai.model.scheam.SchemaSlimmer;
import com.sht.zdaicode.constant.AppConstant;
import com.sht.zdaicode.core.AiCodeGeneratorFacade;
import com.sht.zdaicode.langgraph4j.state.WorkflowContext;
import com.sht.zdaicode.model.enums.CodeGenTypeEnum;
import com.sht.zdaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 全栈应用：Vue 前端代码生成节点 (API契约驱动)
 * <p>
 * 使用 {@link SchemaSlimmer} 对完整的后端 Schema 进行精炼，
 * 仅将前端需要的字段信息传递给大模型，减少上下文噪声和幻觉概率。
 * </p>
 */
@Slf4j
public class FrontendCodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 全栈前端代码生成 (基于API契约与动态路由)");

            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();

            try {
                // 1. 获取全栈 Schema 并进行精炼
                ProjectScheam schema = context.getProjectSchema();
                if (schema == null) {
                    throw new RuntimeException("未能获取到 ProjectSchema，前端生成缺失 API 契约！");
                }

                // ★ Schema 精炼：剥离后端专属字段，减少 50-80% 的上下文 token
                FrontendSchemaView frontendSchema = SchemaSlimmer.slim(schema);
                String slimSchemaJson = JSONUtil.toJsonStr(frontendSchema);
                String fullSchemaJson = JSONUtil.toJsonStr(schema);
                double compressionRate = (1.0 - (double) slimSchemaJson.length() / fullSchemaJson.length()) * 100;
                log.info("Schema 精炼效果: 原始 {} 字符 → 精炼后 {} 字符 (压缩率 {}%)",
                        fullSchemaJson.length(), slimSchemaJson.length(),
                        String.format("%.1f", compressionRate));

                // 2. 调用路由判断生成类型 (路由也使用精炼后的 Schema，减少路由推理噪声)
                AiCodeGenTypeRoutingService router = SpringContextUtil.getBean(
                        com.sht.zdaicode.ai.AiCodeGenTypeRoutingServiceFactory.class).createAiCodeGenTypeRoutingService();
                String routingInput = "【用户需求与素材】\n" + context.getEnhancedPrompt()
                        + "\n\n【前端实体概要】\n" + buildEntitySummaryForRouting(frontendSchema);
                
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n\n> 🚦 正在评估前端项目复杂度，进行智能路由...\n\n");
                }
                
                CodeGenTypeEnum generationType = router.routeFullStackCodeGenType(routingInput);
                log.info("前端路由决策结果: {}", generationType.getValue());
                context.setFrontendGenerationType(generationType);

                // 3. 构造前端全栈架构师提示词 (使用精炼后的 Schema)
                String frontendPrompt = buildFrontendPrompt(generationType, slimSchemaJson, context.getEnhancedPrompt());

                // 通知前端交互界面
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept(String.format(
                            "\n\n> 💻 路由选择完成，开始以 [%s] 模式生成前端工程及接口绑定代码...\n\n",
                            generationType.getText()));
                }

                // 4. 判断生成模式并执行代码生成
                Long appId = context.getAppId() != null ? context.getAppId() : 0L;
                
                if (generationType == CodeGenTypeEnum.FRONTEND_FULLSTACK_VUE) {
                    // 分批生成模式
                    ChunkedFrontendGenerator chunkedGenerator = new ChunkedFrontendGenerator(
                            frontendSchema, appId, context.getTokenEmitter());
                    
                    chunkedGenerator.execute().whenComplete((v, error) -> {
                        if (error != null) {
                            log.error("全栈前端分批生成失败", error);
                            if (context.getTokenEmitter() != null) {
                                context.getTokenEmitter().accept("\n\n❌ [前端生成中断: " + error.getMessage() + "]\n");
                            }
                            future.completeExceptionally(error);
                        } else {
                            log.info("✅ 全栈前端代码分批生成完成");

                            // 同步构建 Vue 项目
                            com.sht.zdaicode.core.builder.VueProjectBuilder vueProjectBuilder = 
                                    SpringContextUtil.getBean(com.sht.zdaicode.core.builder.VueProjectBuilder.class);
                            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_create_" + appId;
                            vueProjectBuilder.buildProject(projectPath);

                            // 根据不同模式，设置最终生成的目录
                            String frontendCodeDir = String.format("%s/%s_%s",
                                    AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId);

                            context.setCurrentStep("前端代码生成");
                            // 设置该目录，供最后的 ProjectBuilderNode 或后续质检节点读取
                            context.setFrontendGeneratedCodeDir(frontendCodeDir);

                            future.complete(WorkflowContext.saveContext(context));
                        }
                    });
                } else {
                    // 原有的单次全量生成逻辑 (如 FRONTEND_FULLSTACK_HTML)
                    AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
                    StringBuilder fullCode = new StringBuilder();

                    Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(
                            frontendPrompt, generationType, appId);

                    codeStream.subscribe(
                            token -> {
                                if (context.getTokenEmitter() != null) {
                                    context.getTokenEmitter().accept(token);
                                }
                                fullCode.append(token);
                            },
                            err -> {
                                log.error("全栈前端代码流式生成失败", err);
                                if (context.getTokenEmitter() != null) {
                                    context.getTokenEmitter().accept("\n\n❌ [前端生成中断: " + err.getMessage() + "]\n");
                                }
                                future.completeExceptionally(err);
                            },
                            () -> {
                                log.info("✅ 全栈前端代码生成完成");

                                String frontendCodeDir = String.format("%s/%s_%s",
                                        AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId);

                                context.setCurrentStep("前端代码生成");
                                context.setFrontendGeneratedCodeDir(frontendCodeDir);

                                future.complete(WorkflowContext.saveContext(context));
                            }
                    );
                }

            } catch (Exception e) {
                log.error("前端节点执行发生异常: {}", e.getMessage(), e);
                future.completeExceptionally(e);
            }

            return future;
        };
    }

    /**
     * 为路由节点构建极简的实体概要 (仅包含实体名、描述、字段数量)
     * 路由只需要判断项目复杂度，不需要完整的字段列表
     */
    private static String buildEntitySummaryForRouting(FrontendSchemaView schema) {
        if (schema.getEntities() == null || schema.getEntities().isEmpty()) {
            return "无实体";
        }
        return schema.getEntities().stream()
                .map(e -> String.format("- %s (%s): %d 个字段",
                        e.getEntityName(), e.getLabel(), e.getFields() != null ? e.getFields().size() : 0))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 将精炼后的 FrontendSchemaView 和用户需求组合，转换成前端大模型的 Prompt
     * <p>
     * 精炼后的 Schema 已包含: entityName, apiBasePath, primaryKey, fields(name/label/type/required)
     * 不再包含: dbType, isAutoIncrement, columnName, isNullable 等后端专属字段
     * </p>
     */
    private static String buildFrontendPrompt(CodeGenTypeEnum type, String slimSchemaJson, String userRequirement) {
        String requirement = userRequirement != null ? userRequirement : "无特定业务需求补充";

        if (type == CodeGenTypeEnum.FRONTEND_FULLSTACK_VUE) {
            return String.format(
                    "你是一个拥有10年经验的 Vue3 全栈前端专家。\n" +
                    "你的任务是根据下方提供的【前端数据模型】，配合用户的业务需求，编写一套真实可用的前端管理系统代码。\n\n" +
                    "【用户业务需求与图片素材】\n%s\n\n" +
                    "【前端数据模型 (JSON)】\n%s\n\n" +
                    "上方 JSON 说明：\n" +
                    "- entityName: 实体名称 (用于命名文件和组件)\n" +
                    "- apiBasePath: 该实体对应的后端 API 基路径\n" +
                    "- primaryKey: 主键字段名\n" +
                    "- fields: 需要在表格和表单中展示的字段列表，其中 name 是字段名、label 是中文标签、type 是数据类型(string/number/boolean/date/text)、required 是是否必填\n\n" +
                    "请严格参考 System Prompt 中的【API 契约约束】，为每一个 Entity 编写 `src/api/xxx.ts` 和增删改查视图 `src/views/xxx/Index.vue`。\n" +
                    "要求界面美观、交互友好，所有表单必须带有基础的空值校验。\n" +
                    "请直接调用 writeFile 工具生成代码文件，不要在对话框中只做代码展示。",
                    requirement, slimSchemaJson
            );
        } else {
            return String.format(
                    "你是一个拥有10年经验的全栈前端专家。\n" +
                    "你的任务是根据下方提供的【前端数据模型】，配合用户的业务需求，编写一套真实可用的原生前端代码页面。\n\n" +
                    "【用户业务需求与图片素材】\n%s\n\n" +
                    "【前端数据模型 (JSON)】\n%s\n\n" +
                    "上方 JSON 说明：\n" +
                    "- entityName: 实体名称\n" +
                    "- apiBasePath: 该实体对应的后端 API 基路径 (请用 fetch 或 axios 请求这些路径)\n" +
                    "- primaryKey: 主键字段名\n" +
                    "- fields: 页面展示字段列表，name 是字段名、label 是中文标签、type 是数据类型、required 是是否必填\n\n" +
                    "请严格参考 System Prompt 的约束，必须使用 fetch 或 axios 请求 apiBasePath 指定的后端接口来绑定真实数据。\n" +
                    "要求界面美观、交互友好。\n",
                    requirement, slimSchemaJson
            );
        }
    }
}