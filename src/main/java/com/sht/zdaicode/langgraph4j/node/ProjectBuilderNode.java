package com.sht.zdaicode.langgraph4j.node;

import com.sht.zdaicode.core.builder.JavaProjectBuilder;
import com.sht.zdaicode.core.builder.VueProjectBuilder;
import com.sht.zdaicode.exception.BusinessException;
import com.sht.zdaicode.exception.ErrorCode;
import com.sht.zdaicode.langgraph4j.state.WorkflowContext;
import com.sht.zdaicode.model.enums.CodeGenTypeEnum;
import com.sht.zdaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class ProjectBuilderNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 项目构建");

            String generatedCodeDir = context.getGeneratedCodeDir();
            String buildResultDir = generatedCodeDir; // 默认保留原路径

            try {
                // 判断当前结构，如果是全栈模式，应该存在 frontend 或 backend 文件夹
                File frontendDir = new File(generatedCodeDir, "frontend");
                File backendDir = new File(generatedCodeDir, "backend");

                if (frontendDir.exists() || backendDir.exists()) {
                    if (context.getTokenEmitter() != null) {
                        context.getTokenEmitter().accept("\n> 🚀 开始全栈工程自动化构建...\n");
                    }

                    // 1. 构建前端
                    if (frontendDir.exists()) {
                        VueProjectBuilder vueBuilder = SpringContextUtil.getBean(VueProjectBuilder.class);
                        if (context.getTokenEmitter() != null) {
                            context.getTokenEmitter().accept("\n> 💻 正在执行前端构建 (npm run build)...\n");
                        }
                        boolean vueSuccess = vueBuilder.buildProject(frontendDir.getAbsolutePath());
                        if (vueSuccess) {
                            log.info("Vue 项目构建成功，dist 目录: {}", new File(frontendDir, "dist").getAbsolutePath());
                            if (context.getTokenEmitter() != null) {
                                context.getTokenEmitter().accept("\n> ✅ 前端构建成功！\n");
                            }
                        } else {
                            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败");
                        }
                    }

                    // 2. 构建后端
                    if (backendDir.exists()) {
                        JavaProjectBuilder javaBuilder = SpringContextUtil.getBean(JavaProjectBuilder.class);
                        if (context.getTokenEmitter() != null) {
                            context.getTokenEmitter().accept("\n> ☕ 正在执行后端构建 (mvn clean package)...\n");
                        }
                        boolean javaSuccess = javaBuilder.buildProject(backendDir.getAbsolutePath());
                        if (javaSuccess) {
                            log.info("Java 项目构建成功，target 目录: {}", new File(backendDir, "target").getAbsolutePath());
                            if (context.getTokenEmitter() != null) {
                                context.getTokenEmitter().accept("\n> ✅ 后端构建成功！\n");
                            }
                        } else {
                            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Java 后端项目构建失败");
                        }
                    }

                } else {
                    // 退化为普通单体前端 Vue 项目构建 (向后兼容)
                    VueProjectBuilder vueBuilder = SpringContextUtil.getBean(VueProjectBuilder.class);
                    if (context.getTokenEmitter() != null) {
                        context.getTokenEmitter().accept("\n> 💻 正在执行单工程构建 (npm run build)...\n");
                    }
                    boolean buildSuccess = vueBuilder.buildProject(generatedCodeDir);
                    if (buildSuccess) {
                        buildResultDir = generatedCodeDir + File.separator + "dist";
                        log.info("Vue 项目构建成功，dist 目录: {}", buildResultDir);
                        if (context.getTokenEmitter() != null) {
                            context.getTokenEmitter().accept("\n> ✅ 构建成功！\n");
                        }
                    } else {
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败");
                    }
                }
            } catch (Exception e) {
                log.error("项目构建异常: {}", e.getMessage(), e);
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n> ❌ 项目构建发生异常: " + e.getMessage() + "\n");
                }
                buildResultDir = generatedCodeDir; // 异常时返回原路径
            }

            // 更新状态
            context.setCurrentStep("项目构建");
            context.setBuildResultDir(buildResultDir);
            log.info("项目构建节点完成，最终主目录: {}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }
}
