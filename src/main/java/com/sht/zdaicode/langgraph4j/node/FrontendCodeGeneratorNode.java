package com.sht.zdaicode.langgraph4j.node;

import cn.hutool.json.JSONUtil;
import com.sht.zdaicode.ai.model.scheam.ProjectScheam;
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

/**
 * 全栈应用：Vue 前端代码生成节点 (API契约驱动)
 */
@Slf4j
public class FrontendCodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: Vue 前端代码生成 (基于API契约)");

            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();

            try {
                // 1. 获取全栈 Schema 作为数据契约
                ProjectScheam schema = context.getProjectSchema();
                if (schema == null) {
                    throw new RuntimeException("未能获取到 ProjectSchema，前端生成缺失 API 契约！");
                }

                // 2. 构造前端全栈架构师提示词
                String frontendPrompt = buildVueFrontendPrompt(schema, context.getEnhancedPrompt());

                // 通知前端交互界面
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n\n> 💻 正在初始化 Vue3 + Vite 前端工程，根据后端 API 契约自动生成 Axios 请求与交互视图...\n\n");
                }

                // 3. 获取 AI 流式代码生成服务
                AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);

                // 触发全栈前端生成
                CodeGenTypeEnum generationType = CodeGenTypeEnum.valueOf("FRONTEND_FULLSTACK");
                Long appId = context.getAppId() != null ? context.getAppId() : 0L;

                StringBuilder fullCode = new StringBuilder();

                // 4. 调用大模型并发写入前端代码
                Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(frontendPrompt, generationType, appId);

                codeStream.subscribe(
                        token -> {
                            if (context.getTokenEmitter() != null) {
                                context.getTokenEmitter().accept(token);
                            }
                            fullCode.append(token);
                        },
                        error -> {
                            log.error("Vue 前端代码流式生成失败", error);
                            if (context.getTokenEmitter() != null) {
                                context.getTokenEmitter().accept("\n\n❌ [前端生成中断: " + error.getMessage() + "]\n");
                            }
                            future.completeExceptionally(error);
                        },
                        () -> {
                            log.info("✅ Vue 前端代码生成完成");

                            // 【重要修复】：目录必须与底层 FileWriteTool 创建的目录保持完全一致
                            // 因为 Facade 里复用了 VUE_PROJECT_CREATE，所以底层的存放目录是 vue_project_create_ 加上 appId
                            String vueCreateScenarioValue = CodeGenTypeEnum.VUE_PROJECT_CREATE.getValue(); // "vue_project_create"
                            String frontendCodeDir = String.format("%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, vueCreateScenarioValue, appId);

                            context.setCurrentStep("前端代码生成");
                            // 设置该目录，供最后的 ProjectBuilderNode 进行 npm run build
                            context.setFrontendCodeDir(frontendCodeDir);

                            future.complete(WorkflowContext.saveContext(context));
                        }
                );

            } catch (Exception e) {
                log.error("前端节点执行发生异常: {}", e.getMessage(), e);
                future.completeExceptionally(e);
            }

            return future;
        };
    }

    /**
     * 将 ProjectSchema 和用户需求组合，转换成前端大模型的 Prompt
     */
    private static String buildVueFrontendPrompt(ProjectScheam schema, String userRequirement) {
        String schemaJson = JSONUtil.toJsonStr(schema);

        return String.format(
                "你是一个拥有10年经验的 Vue3 全栈前端专家。\n" +
                        "你的任务是根据后端生成的【全栈数据库 Schema】，配合用户的业务需求，编写一套真实可用的前端管理系统代码。\n\n" +
                        "【用户原始业务需求】\n%s\n\n" +
                        "【后端 API 契约与表结构 (JSON)】\n%s\n\n" +
                        "请严格参考 System Prompt 中的【API 契约约束】，为上方 JSON 中的每一个 Entity 编写对应的 `src/api/xxx.ts` 和完整的增删改查视图 `src/views/xxx/Index.vue`。\n" +
                        "要求界面美观、交互友好，所有表单必须带有基础的空值校验。\n" +
                        "请直接调用 writeFile 工具生成代码文件，不要在对话框中只做代码展示。",
                userRequirement != null ? userRequirement : "无特定业务需求补充",
                schemaJson
        );
    }
}