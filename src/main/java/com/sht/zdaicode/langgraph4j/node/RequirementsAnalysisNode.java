package com.sht.zdaicode.langgraph4j.node;

import cn.hutool.core.util.StrUtil;
import com.sht.zdaicode.ai.RequirementsAnalysisAiService;
import com.sht.zdaicode.ai.RequirementsAnalysisAiServiceFactory;
import com.sht.zdaicode.exception.BusinessException;
import com.sht.zdaicode.exception.ErrorCode;
import com.sht.zdaicode.langgraph4j.state.WorkflowContext;
import com.sht.zdaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class RequirementsAnalysisNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 需求分析");

            try {
                String userMessage = context.getOriginalPrompt();
                if (StrUtil.isBlank(userMessage)) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "原始需求提示词为空");
                }

                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n> 🧠 正在进行需求分析，扩充和整理项目功能...\n");
                }

                RequirementsAnalysisAiServiceFactory factory = SpringContextUtil.getBean(RequirementsAnalysisAiServiceFactory.class);
                RequirementsAnalysisAiService aiService = factory.createService();
                
                log.info("开始调用 AI 进行需求分析...");
                String analyzedRequirements = aiService.analyzeRequirements(userMessage);

                if (StrUtil.isBlank(analyzedRequirements)) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "需求分析结果为空");
                }
                
                log.info("需求分析完成:\n{}", analyzedRequirements);
                context.setAnalyzedRequirements(analyzedRequirements);
                
                // 为了兼容后续直接使用 enhancedPrompt，我们将需求分析结果也附加到其中
                String enhancedPrompt = context.getEnhancedPrompt() != null ? context.getEnhancedPrompt() + "\n\n详细需求:\n" + analyzedRequirements : analyzedRequirements;
                context.setEnhancedPrompt(enhancedPrompt);

                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n> ✅ 需求分析完成。\n");
                    context.getTokenEmitter().accept("\n```markdown\n" + analyzedRequirements + "\n```\n");
                }
            } catch (Exception e) {
                log.error("需求分析异常: {}", e.getMessage(), e);
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n\n❌ [需求分析失败: " + e.getMessage() + "]\n");
                }
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "需求分析失败: " + e.getMessage());
            }

            context.setCurrentStep("需求分析");
            return WorkflowContext.saveContext(context);
        });
    }
}
