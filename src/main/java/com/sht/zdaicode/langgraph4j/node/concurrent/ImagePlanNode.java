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
public class ImagePlanNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            String originalPrompt = context.getOriginalPrompt();
            
            // 空值检查，确保originalPrompt不为null
            if (originalPrompt == null) {
                log.error("原始提示词为空，跳过图片计划生成");
                context.setCurrentStep("图片计划（跳过，因为提示词为空）");
                return WorkflowContext.saveContext(context);
            }
            
            try {
                // 检查输入长度，避免触发输入防护限制（1000字）
                String promptForImagePlan = originalPrompt;
                if (originalPrompt.length() > 1000) {
                    promptForImagePlan = originalPrompt.substring(0, 1000) + "...";
                    log.warn("用户输入过长，已截取前1000字符用于图片计划生成");
                }
                
                // 获取图片收集计划服务
                ImageCollectionPlanService planService = SpringContextUtil.getBean(ImageCollectionPlanService.class);
                ImageCollectionPlan plan = planService.planImageCollection(promptForImagePlan);
                log.info("生成图片收集计划，准备启动并发分支");
                // 将计划存储到上下文中
                context.setImageCollectionPlan(plan);
                context.setCurrentStep("图片计划");
            } catch (Exception e) {
                log.error("图片计划生成失败: {}", e.getMessage(), e);
            }
            return WorkflowContext.saveContext(context);
        });
    }
}
