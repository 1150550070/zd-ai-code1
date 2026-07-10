package com.sht.zdaicode.langgraph4j.node.concurrent;

import com.sht.zdaicode.langgraph4j.ai.ImageCollectionPlanService;
import com.sht.zdaicode.langgraph4j.model.ImageCollectionPlan;
import com.sht.zdaicode.langgraph4j.state.WorkflowContext;
import com.sht.zdaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class FullStackImagePlanNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            // 这里使用经过需求分析节点扩充后的 enhancedPrompt 或 analyzedRequirements
            String promptSource = context.getEnhancedPrompt() != null ? context.getEnhancedPrompt() : context.getOriginalPrompt();
            
            if (promptSource == null) {
                log.error("提示词为空，跳过图片计划生成");
                context.setCurrentStep("图片计划（跳过，因为提示词为空）");
                return WorkflowContext.saveContext(context);
            }
            
            try {
                // 检查输入长度，避免触发输入防护限制（1000字）
                String promptForImagePlan = promptSource;
                if (promptSource.length() > 1000) {
                    promptForImagePlan = promptSource.substring(0, 1000) + "...";
                    log.warn("分析后的需求输入过长，已截取前1000字符用于图片计划生成");
                }
                
                // 获取图片收集计划服务
                ImageCollectionPlanService planService = SpringContextUtil.getBean(ImageCollectionPlanService.class);
                ImageCollectionPlan plan = planService.planImageCollection(promptForImagePlan);
                log.info("生成全栈图片收集计划，准备启动并发分支");
                // 将计划存储到上下文中
                context.setImageCollectionPlan(plan);
                context.setCurrentStep("全栈图片计划");
            } catch (Exception e) {
                log.error("全栈图片计划生成失败: {}", e.getMessage(), e);
            }
            return WorkflowContext.saveContext(context);
        });
    }
}
