package com.sht.zdaicode.langgraph4j.node;

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

import javax.swing.plaf.nimbus.State;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class BackendCodeGeneratorNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return State -> {
            WorkflowContext context = WorkflowContext.getContext(State);
            log.info("执行节点: Java 后端代码生成");
            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            try {
                // 1. 获取上一步生成的全栈 Schema
                ProjectScheam schema = context.getProjectSchema();
                if (schema == null) {
                    throw new RuntimeException("未能从上下文中获取到 ProjectSchema，请确保数据库设计节点已成功执行！");
                }

                // 通知前端开始生成后端代码
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n\n> ☕ 正在初始化 Java Spring Boot 后端工程，为您编写 Controller, Service, Mapper 与 Entity...\n\n");
                }

                // 2. 获取流式生成外观服务
                AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);

                // 锁定生成类型为后端 Java
                CodeGenTypeEnum generationType = CodeGenTypeEnum.BACKEND_JAVA;
                context.setBackendGenerationType(generationType);
                Long appId = context.getAppId() != null ? context.getAppId() : 0L;

                // 3. 组装提示词
                String schemaJson = cn.hutool.json.JSONUtil.toJsonStr(schema);
                String backendPrompt = String.format(
                        "你是一个高级 Java 架构师。请根据以下【业务需求】和【数据库设计(Schema)】，生成标准的单工程多包结构的 Spring Boot 后端代码。\n\n" +
                                "【业务需求】\n%s\n\n" +
                                "【数据库 Schema】\n%s\n\n" +
                                "请务必生成实体类、Mapper、Service (含实现) 以及 Controller。",
                        context.getEnhancedPrompt() != null ? context.getEnhancedPrompt() : context.getOriginalPrompt(),
                        schemaJson
                );

                StringBuilder fullCode = new StringBuilder();

                // 4. 调用流式生成 (大模型开始输出多个 Java 文件的 Markdown 块)
                Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(backendPrompt, generationType, appId);

                codeStream.subscribe(
                        token -> {
                            // 实时推送给前端聊天框
                            if (context.getTokenEmitter() != null) {
                                context.getTokenEmitter().accept(token);
                            }
                            fullCode.append(token);
                        },
                        error -> {
                            log.error("Java 后端代码流式生成失败", error);
                            if (context.getTokenEmitter() != null) {
                                context.getTokenEmitter().accept("\n\n❌ [后端生成中断: " + error.getMessage() + "]\n");
                            }
                            future.completeExceptionally(error);
                        },
                        () -> {
                            log.info("✅ Java 后端代码生成完成");
                            // 记录生成目录 (基于你原本的存放逻辑)
                            String backendCodeDir = String.format("%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId);

                            // 更新上下文状态
                            context.setCurrentStep("后端代码生成");
                            context.setBackendGeneratedCodeDir(backendCodeDir); // 如果需要传给后续步骤

                            // 释放 Future，通知 LangGraph 进入下一个节点 (如: 质检节点)
                            future.complete(WorkflowContext.saveContext(context));
                        }
                );
            } catch (Exception e) {
                log.error("后端节点执行发生异常: {}", e.getMessage(), e);
                future.completeExceptionally(e);
            }

            return future;
        };
    }
}
