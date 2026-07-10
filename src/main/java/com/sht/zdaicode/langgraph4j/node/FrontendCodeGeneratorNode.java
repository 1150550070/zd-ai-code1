package com.sht.zdaicode.langgraph4j.node;

import cn.hutool.json.JSONUtil;
import com.sht.zdaicode.ai.AiCodeGenTypeRoutingService;
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
            log.info("执行节点: 全栈前端代码生成 (基于API契约与动态路由)");

            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();

            try {
                // 1. 获取全栈 Schema 作为数据契约
                ProjectScheam schema = context.getProjectSchema();
                if (schema == null) {
                    throw new RuntimeException("未能获取到 ProjectSchema，前端生成缺失 API 契约！");
                }
                String schemaJson = JSONUtil.toJsonStr(schema);

                // 2. 调用路由判断生成类型
                AiCodeGenTypeRoutingService router = SpringContextUtil.getBean(com.sht.zdaicode.ai.AiCodeGenTypeRoutingServiceFactory.class).createAiCodeGenTypeRoutingService();
                String routingInput = "【用户需求与素材】\n" + context.getEnhancedPrompt() + "\n\n【API契约与表结构】\n" + schemaJson;
                
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n\n> 🚦 正在评估前端项目复杂度，进行智能路由...\n\n");
                }
                
                CodeGenTypeEnum generationType = router.routeFullStackCodeGenType(routingInput);
                log.info("前端路由决策结果: {}", generationType.getValue());
                context.setFrontendGenerationType(generationType);

                // 3. 构造前端全栈架构师提示词
                String frontendPrompt = buildFrontendPrompt(generationType, schemaJson, context.getEnhancedPrompt());

                // 通知前端交互界面
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept(String.format("\n\n> 💻 路由选择完成，开始以 [%s] 模式生成前端工程及接口绑定代码...\n\n", generationType.getText()));
                }

                // 4. 获取 AI 流式代码生成服务
                AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
                Long appId = context.getAppId() != null ? context.getAppId() : 0L;
                StringBuilder fullCode = new StringBuilder();

                // 5. 调用大模型并发写入前端代码
                Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(frontendPrompt, generationType, appId);

                codeStream.subscribe(
                        token -> {
                            if (context.getTokenEmitter() != null) {
                                context.getTokenEmitter().accept(token);
                            }
                            fullCode.append(token);
                        },
                        error -> {
                            log.error("全栈前端代码流式生成失败", error);
                            if (context.getTokenEmitter() != null) {
                                context.getTokenEmitter().accept("\n\n❌ [前端生成中断: " + error.getMessage() + "]\n");
                            }
                            future.completeExceptionally(error);
                        },
                        () -> {
                            log.info("✅ 全栈前端代码生成完成");

                            // 根据不同模式，设置最终生成的目录
                            String frontendCodeDir = String.format("%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId);

                            context.setCurrentStep("前端代码生成");
                            // 设置该目录，供最后的 ProjectBuilderNode 或后续质检节点读取
                            context.setFrontendGeneratedCodeDir(frontendCodeDir);

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
    private static String buildFrontendPrompt(CodeGenTypeEnum type, String schemaJson, String userRequirement) {
        if (type == CodeGenTypeEnum.FRONTEND_FULLSTACK_VUE) {
            return String.format(
                    "你是一个拥有10年经验的 Vue3 全栈前端专家。\n" +
                            "你的任务是根据后端生成的【全栈数据库 Schema】，配合用户的业务需求，编写一套真实可用的前端管理系统代码。\n\n" +
                            "【用户业务需求与图片素材】\n%s\n\n" +
                            "【后端 API 契约与表结构 (JSON)】\n%s\n\n" +
                            "请严格参考 System Prompt 中的【API 契约约束】，为上方 JSON 中的每一个 Entity 编写对应的 `src/api/xxx.ts` 和完整的增删改查视图 `src/views/xxx/Index.vue`。\n" +
                            "要求界面美观、交互友好，所有表单必须带有基础的空值校验。\n" +
                            "请直接调用 writeFile 工具生成代码文件，不要在对话框中只做代码展示。",
                    userRequirement != null ? userRequirement : "无特定业务需求补充",
                    schemaJson
            );
        } else {
            return String.format(
                    "你是一个拥有10年经验的全栈前端专家。\n" +
                            "你的任务是根据后端生成的【全栈数据库 Schema】，配合用户的业务需求，编写一套真实可用的原生前端代码页面。\n\n" +
                            "【用户业务需求与图片素材】\n%s\n\n" +
                            "【后端 API 契约与表结构 (JSON)】\n%s\n\n" +
                            "请严格参考 System Prompt 的约束，必须使用 fetch 或 axios 请求契约中的后端接口来绑定真实数据。要求界面美观、交互友好。\n",
                    userRequirement != null ? userRequirement : "无特定业务需求补充",
                    schemaJson
            );
        }
    }
}