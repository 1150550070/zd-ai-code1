package com.sht.zdaicode.langgraph4j.node;

import com.sht.zdaicode.constant.AppConstant;
import com.sht.zdaicode.core.AiCodeGeneratorFacade;
import com.sht.zdaicode.langgraph4j.model.QualityResult;
import com.sht.zdaicode.langgraph4j.state.WorkflowContext;
import com.sht.zdaicode.model.enums.CodeGenTypeEnum;
import com.sht.zdaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class CodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码生成");

            // 构造用户消息（包含原始提示词和可能的错误修复信息）
            String userMessage = buildUserMessage(context);

            CodeGenTypeEnum generationType = context.getGenerationType();
            // 获取 AI 代码生成外观服务
            AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            log.info("开始生成代码，类型: {} ({})", generationType.getValue(), generationType.getText());
            // 从上下文中获取真实的 appId，并使用三元运算保证只赋值一次 (有效 final)
            Long rawAppId = context.getAppId();
            Long appId = (rawAppId == null || rawAppId == 0L) ? 0L : rawAppId;

            if (appId == 0L) {
                log.warn("WorkflowContext 中的 appId 为空或为0，使用默认值 0L");
            }
            // 创建一个 CompletableFuture，用于挂起图执行引擎，等待流式任务完成
            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            StringBuilder fullCode = new StringBuilder();

            // 修改 2：通知前端准备接收代码块
            if (context.getTokenEmitter() != null) {
                context.getTokenEmitter().accept("\n```vue\n");
            }

            // 调用流式代码生成
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(userMessage, generationType, appId);

            codeStream.subscribe(
                    token -> {
                        // 每产生一个字，就通过 WorkflowContext 里绑定的 sink 实时推给前端
                        if (context.getTokenEmitter() != null) {
                            context.getTokenEmitter().accept(token);
                        }
                        // 把代码收集起来，供后续质检节点使用
                        fullCode.append(token);
                    },
                    // 发生异常时：onError
                    error -> {
                        log.error("流式代码生成失败", error);
                        if (context.getTokenEmitter() != null) {
                            context.getTokenEmitter().accept("\n\n❌ [AI生成中断: " + error.getMessage() + "]\n");
                        }
                        // 必须标记 future 异常，否则工作流会死锁
                        future.completeExceptionally(error);
                    },
                    // 流完成时：onComplete
                    () -> {
                        // 通知前端代码块闭合
                        if (context.getTokenEmitter() != null) {
                            context.getTokenEmitter().accept("\n```\n\n");
                        }


                        // 根据类型设置生成目录
                        String generatedCodeDir = String.format("%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId);
                        log.info("AI 代码生成完成，生成目录: {}", generatedCodeDir);

                        // 更新状态
                        context.setCurrentStep("代码生成");
                        context.setGeneratedCodeDir(generatedCodeDir);
                        context.setGeneratedCode(fullCode.toString());
                        // 释放 Future，正式通知 LangGraph 工作流进入下一步
                        future.complete(WorkflowContext.saveContext(context));
                    }
            );
        return future;
        };
    }

    /**
     * 构造用户消息，如果存在质检失败结果则添加错误修复信息
     */
    private static String buildUserMessage(WorkflowContext context) {
        String userMessage = context.getEnhancedPrompt();
        // 检查是否存在质检失败结果
        QualityResult qualityResult = context.getQualityResult();
        if (isQualityCheckFailed(qualityResult)) {
            // 直接将错误修复信息作为新的提示词（起到了修改的作用）
            userMessage = buildErrorFixPrompt(qualityResult);
        }
        return userMessage;
    }

    /**
     * 判断质检是否失败
     */
    private static boolean isQualityCheckFailed(QualityResult qualityResult) {
        return qualityResult != null &&
                !qualityResult.getIsValid() &&
                qualityResult.getErrors() != null &&
                !qualityResult.getErrors().isEmpty();
    }

    /**
     * 构造错误修复提示词
     */
    private static String buildErrorFixPrompt(QualityResult qualityResult) {
        StringBuilder errorInfo = new StringBuilder();
        errorInfo.append("\n\n## 上次生成的代码存在以下问题，请修复：\n");
        // 添加错误列表
        qualityResult.getErrors().forEach(error ->
                errorInfo.append("- ").append(error).append("\n"));
        // 添加修复建议（如果有）
        if (qualityResult.getSuggestions() != null && !qualityResult.getSuggestions().isEmpty()) {
            errorInfo.append("\n## 修复建议：\n");
            qualityResult.getSuggestions().forEach(suggestion ->
                    errorInfo.append("- ").append(suggestion).append("\n"));
        }
        errorInfo.append("\n请根据上述问题和建议重新生成代码，确保修复所有提到的问题。");
        return errorInfo.toString();
    }

}
