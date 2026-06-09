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
        return node_async(State -> {
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

                // 3. 获取流式生成外观服务 (复用你现成的工具)
                AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);

                // 这里假设你在 CodeGenTypeEnum 中新增了 BACKEND_JAVA 类型
                CodeGenTypeEnum generationType = context.getGenerationType();
                Long appId = context.getAppId() != null ? context.getAppId() : 0L;

                StringBuilder fullCode = new StringBuilder();

                // 4. 调用流式生成 (大模型开始输出多个 Java 文件的 Markdown 块)
                Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(context.getOriginalPrompt(), generationType, appId);

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
                            context.setBackendCodeDir(backendCodeDir); // 如果需要传给后续步骤

                            // 释放 Future，通知 LangGraph 进入下一个节点 (如: 前端生成或 Docker 部署节点)
                            future.complete(WorkflowContext.saveContext(context));
                        }
                );
            } catch (Exception e) {

            }

            return WorkflowContext.saveContext(context);
        });
    }
}
