package com.sht.zdaicode.langgraph4j;

import com.sht.zdaicode.exception.BusinessException;
import com.sht.zdaicode.exception.ErrorCode;
import com.sht.zdaicode.langgraph4j.model.QualityResult;
import com.sht.zdaicode.langgraph4j.node.*;
import com.sht.zdaicode.langgraph4j.node.concurrent.*;
import com.sht.zdaicode.langgraph4j.state.WorkflowContext;
import com.sht.zdaicode.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;

import java.util.Map;

import reactor.core.publisher.Flux;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Slf4j
public class CodeGenConcurrentWorkflow {




    /**
     * 创建并发工作流
     */
    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try {
            return new MessagesStateGraph<String>()
                    // 添加节点
                    .addNode("image_plan", ImagePlanNode.create())
                    .addNode("prompt_enhancer", PromptEnhancerNode.create())
                    .addNode("router", RouterNode.create())
                    .addNode("code_generator", CodeGeneratorNode.create())
                    .addNode("code_quality_check", CodeQualityCheckNode.create())
                    .addNode("project_builder", ProjectBuilderNode.create())

                    // 添加并发图片收集节点
                    .addNode("content_image_collector", ContentImageCollectorNode.create())
                    .addNode("illustration_collector", IllustrationCollectorNode.create())
                    .addNode("diagram_collector", DiagramCollectorNode.create())
                    .addNode("logo_collector", LogoCollectorNode.create())
                    .addNode("image_aggregator", ImageAggregatorNode.create())

                    // 添加边
                    .addEdge(START, "image_plan")

                    // 并发分支：从计划节点分发到各个收集节点
                    .addEdge("image_plan", "content_image_collector")
                    .addEdge("image_plan", "illustration_collector")
                    .addEdge("image_plan", "diagram_collector")
                    .addEdge("image_plan", "logo_collector")

                    // 汇聚：所有收集节点都汇聚到聚合器
                    .addEdge("content_image_collector", "image_aggregator")
                    .addEdge("illustration_collector", "image_aggregator")
                    .addEdge("diagram_collector", "image_aggregator")
                    .addEdge("logo_collector", "image_aggregator")

                    // 继续串行流程
                    .addEdge("image_aggregator", "prompt_enhancer")
                    .addEdge("prompt_enhancer", "router")
                    .addEdge("router", "code_generator")
                    .addEdge("code_generator", "code_quality_check")

                    // 质检条件边
                    .addConditionalEdges("code_quality_check",
                            edge_async(this::routeAfterQualityCheck),
                            Map.of(
                                    "build", "project_builder",
                                    "skip_build", END,
                                    "fail", "code_generator"
                            ))
                    .addEdge("project_builder", END)
                    .compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "并发工作流创建失败");
        }
    }

    /**
     * 执行并发工作流 - Flux流式返回
     */
    public Flux<String> executeWorkflowWithFlux(String originalPrompt, Long appId) {
        return Flux.create(sink -> {
            try {
                CompiledGraph<MessagesState<String>> workflow = createWorkflow();
                WorkflowContext initialContext = WorkflowContext.builder()
                        .originalPrompt(originalPrompt)
                        .appId(appId)
                        .currentStep("初始化")
                        .build();

                log.info("开始执行并发代码生成工作流 - Flux流式输出");

                // 发送开始消息
                sink.next("🚀 **开始执行Agent模式代码生成** ");
                sink.next("💭 **思考过程：**正在分析您的需求... ");

                int stepCounter = 1;
                WorkflowContext finalContext = null;

                for (NodeOutput<MessagesState<String>> step : workflow.stream(
                        Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext)
                )) {
                    WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                    if (currentContext != null) {
                        finalContext = currentContext;

                        // 发送用户友好的步骤信息
                        String stepMessage = formatStepMessage(stepCounter, currentContext.getCurrentStep());
                        sink.next(stepMessage);

                        log.info("--- 第 {} 步完成: {} ---", stepCounter, currentContext.getCurrentStep());
                    }
                    stepCounter++;
                }

                // 发送完成消息
                if (finalContext != null && finalContext.getGeneratedCode() != null) {
                    sink.next("✅ **代码生成完成！** ");
                    sink.next("📝 **生成的代码：** ");
                    sink.next(finalContext.getGeneratedCode());
                } else {
                    sink.next("❌ **代码生成失败，请重试** ");
                }

                sink.complete();
                log.info("并发代码生成工作流执行完成！");

            } catch (Exception e) {
                log.error("并发工作流执行失败", e);
                sink.next("❌ **执行失败：** " + e.getMessage() + " ");
                sink.error(e);
            }
        });
    }

    /**
     * 格式化步骤消息为用户友好的格式
     */
    private String formatStepMessage(int stepNumber, String stepName) {
        String emoji = getStepEmoji(stepName);
        String description = getStepDescription(stepName);
        return String.format("%s **步骤 %d：%s** %s ", emoji, stepNumber, stepName, description);
    }

    /**
     * 获取步骤对应的emoji
     */
    private String getStepEmoji(String stepName) {
        switch (stepName) {
            case "初始化": return "🔧";
            case "图片规划": return "🎨";
            case "内容图片收集": return "📸";
            case "插图收集": return "🖼️";
            case "图表收集": return "📊";
            case "Logo收集": return "🏷️";
            case "图片聚合": return "🔗";
            case "提示词增强": return "✨";
            case "路由": return "🛤️";
            case "代码生成": return "💻";
            case "代码质量检查": return "🔍";
            case "项目构建": return "🏗️";
            default: return "⚙️";
        }
    }

    /**
     * 获取步骤描述
     */
    private String getStepDescription(String stepName) {
        switch (stepName) {
            case "初始化": return "正在初始化工作流环境...";
            case "图片规划": return "🔍 分析项目需求，制定图片收集策略";
            case "内容图片收集": return "🌐 并发搜索相关内容图片资源";
            case "插图收集": return "🎭 并发收集装饰性插图素材";
            case "图表收集": return "📈 并发获取数据可视化图表";
            case "Logo收集": return "🎯 并发搜索品牌标识素材";
            case "图片聚合": return "🔄 整合所有收集到的图片资源";
            case "提示词增强": return "🚀 基于图片资源优化代码生成提示词";
            case "路由": return "🎯 智能路由到最适合的代码生成策略";
            case "代码生成": return "⚡ 使用AI生成高质量代码";
            case "代码质量检查": return "🔬 检查代码质量和规范性";
            case "项目构建": return "🔨 构建完整的项目结构";
            default: return "正在处理...";
        }
    }





    /**
     * 执行并发工作流
     */
    public WorkflowContext executeWorkflow(String originalPrompt) {
        CompiledGraph<MessagesState<String>> workflow = createWorkflow();
        WorkflowContext initialContext = WorkflowContext.builder()
                .originalPrompt(originalPrompt)
                .currentStep("初始化")
                .appId(0L) // 默认appId，用于测试
                .build();
        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("并发工作流图:\n{}", graph.content());
        log.info("开始执行并发代码生成工作流");
        WorkflowContext finalContext = null;
        int stepCounter = 1;
        for (NodeOutput<MessagesState<String>> step : workflow.stream(
                Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext)
        )) {
            log.info("--- 第 {} 步完成 ---", stepCounter);
            WorkflowContext currentContext = WorkflowContext.getContext(step.state());
            if (currentContext != null) {
                finalContext = currentContext;
                log.info("当前步骤上下文: {}", currentContext);
            }
            stepCounter++;
        }
        log.info("并发代码生成工作流执行完成！");
        return finalContext;
    }

    /**
     * 路由函数：根据质检结果决定下一步
     */
    private String routeAfterQualityCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult qualityResult = context.getQualityResult();

        if (qualityResult == null || !qualityResult.getIsValid()) {
            log.error("代码质检失败，需要重新生成代码");
            return "fail";
        }
        log.info("代码质检通过，继续后续流程");
        CodeGenTypeEnum generationType = context.getGenerationType();
        if (generationType == CodeGenTypeEnum.VUE_PROJECT_CREATE ) {
            return "build";
        } else {
            return "skip_build";
        }
    }
}



