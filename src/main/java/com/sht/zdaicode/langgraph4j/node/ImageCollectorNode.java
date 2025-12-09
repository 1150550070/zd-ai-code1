package com.sht.zdaicode.langgraph4j.node;

import cn.hutool.core.date.StopWatch;
import com.sht.zdaicode.langgraph4j.ai.ImageCollectionPlanService;
import com.sht.zdaicode.langgraph4j.ai.ImageCollectionService;
import com.sht.zdaicode.langgraph4j.model.ImageCollectionPlan;
import com.sht.zdaicode.langgraph4j.model.enums.ImageCategoryEnum;
import com.sht.zdaicode.langgraph4j.model.ImageResource;
import com.sht.zdaicode.langgraph4j.state.WorkflowContext;
import com.sht.zdaicode.langgraph4j.tools.ImageSearchTool;
import com.sht.zdaicode.langgraph4j.tools.LogoGeneratorTool;
import com.sht.zdaicode.langgraph4j.tools.MermaidDiagramTool;
import com.sht.zdaicode.langgraph4j.tools.UndrawIllustrationTool;
import com.sht.zdaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片收集节点 并发
 * 负责根据用户需求并发执行图片收集任务
 */
@Slf4j
public class ImageCollectorNode {
    /**
     * 通用的任务添加方法，用于将各种类型的图片任务添加到CompletableFuture列表中
     * 
     * @param futures CompletableFuture列表
     * @param tasks 任务列表
     * @param taskFunction 任务执行函数
     * @param <T> 任务类型
     */
    private static <T> void addTasksToFutures(List<CompletableFuture<List<ImageResource>>> futures, 
                                             List<T> tasks, 
                                             java.util.function.Function<T, List<ImageResource>> taskFunction) {
        for (T task : tasks) {
            final T currentTask = task; // 避免lambda中的变量引用问题
            futures.add(CompletableFuture.supplyAsync(
                () -> taskFunction.apply(currentTask),
                IMAGE_COLLECTION_POOL
            ));
        }
    }
    
    // 自定义线程池，专门用于图片收集任务
    private static final ThreadPoolExecutor IMAGE_COLLECTION_POOL = new ThreadPoolExecutor(
        10,  // 核心线程数
        20,  // 最大线程数
        60L, TimeUnit.SECONDS,  // 空闲线程存活时间
        new LinkedBlockingQueue<>(100),  // 工作队列
        new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "Image-Collector-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        },
        new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
    );

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            String originalPrompt = context.getOriginalPrompt();
            List<ImageResource> collectedImages = new ArrayList<>();
            
            // 空值检查，确保originalPrompt不为null
            if (originalPrompt == null) {
                log.error("原始提示词为空，跳过图片收集");
                context.setCurrentStep("图片收集（跳过，因为提示词为空）");
                context.setImageList(collectedImages);
                return WorkflowContext.saveContext(context);
            }

            // 开头计时
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            try {
                // 第一步：获取图片收集计划
                // 检查输入长度，避免触发输入防护限制（5000字）
                String promptForImagePlan = originalPrompt;
                if (originalPrompt.length() > 5000) {
                    promptForImagePlan = originalPrompt.substring(0, 5000) + "...";
                    log.warn("用户输入过长，已截取前5000字符用于图片计划生成");
                }
                ImageCollectionPlanService planService = SpringContextUtil.getBean(ImageCollectionPlanService.class);
                ImageCollectionPlan plan = planService.planImageCollection(promptForImagePlan);
                log.info("获取到图片收集计划，开始并发执行");

                // 第二步：并发执行各种图片收集任务
                List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
                
                // 使用通用方法处理不同类型的图片任务，减少重复代码
                if (plan.getContentImageTasks() != null) {
                    ImageSearchTool imageSearchTool = SpringContextUtil.getBean(ImageSearchTool.class);
                    addTasksToFutures(futures, plan.getContentImageTasks(), 
                        task -> imageSearchTool.searchContentImages(task.query()));
                }
                
                if (plan.getIllustrationTasks() != null) {
                    UndrawIllustrationTool illustrationTool = SpringContextUtil.getBean(UndrawIllustrationTool.class);
                    addTasksToFutures(futures, plan.getIllustrationTasks(), 
                        task -> illustrationTool.searchIllustrations(task.query()));
                }
                
                if (plan.getDiagramTasks() != null) {
                    MermaidDiagramTool diagramTool = SpringContextUtil.getBean(MermaidDiagramTool.class);
                    addTasksToFutures(futures, plan.getDiagramTasks(), 
                        task -> diagramTool.generateMermaidDiagram(task.mermaidCode(), task.description()));
                }
                
                if (plan.getLogoTasks() != null) {
                    LogoGeneratorTool logoTool = SpringContextUtil.getBean(LogoGeneratorTool.class);
                    addTasksToFutures(futures, plan.getLogoTasks(), 
                        task -> logoTool.generateLogos(task.description()));
                }

                // 等待所有任务完成并收集结果
                CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0]));
                allTasks.join();
                // 收集所有结果
                for (CompletableFuture<List<ImageResource>> future : futures) {
                    List<ImageResource> images = future.get();
                    if (images != null) {
                        collectedImages.addAll(images);
                    }
                }
                log.info("并发图片收集完成，共收集到 {} 张图片", collectedImages.size());
            } catch (Exception e) {
                log.error("图片收集失败: {}", e.getMessage(), e);
            }
            // 结尾停止计时并输出结果
            stopWatch.stop();
            log.info("图片收集总耗时: {} ms", stopWatch.getTotalTimeMillis());
            // 更新状态
            context.setCurrentStep("图片收集");
            context.setImageList(collectedImages);
            return WorkflowContext.saveContext(context);
        });
    }
}


