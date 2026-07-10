package com.sht.zdaicode.langgraph4j;

import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.sht.zdaicode.exception.BusinessException;
import com.sht.zdaicode.exception.ErrorCode;
import com.sht.zdaicode.langgraph4j.model.QualityResult;
import com.sht.zdaicode.langgraph4j.node.*;
import com.sht.zdaicode.langgraph4j.node.concurrent.*;
import com.sht.zdaicode.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Slf4j
public class FullStackCodeGenWorkflow {

    private static final int MAX_RETRIES = 2;

    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try {
            return new MessagesStateGraph<String>()
                    // 注册所有节点
                    .addNode("requirements_analysis", RequirementsAnalysisNode.create())
                    .addNode("database_designer", DatabaseDesignerNode.create())
                    .addNode("db_quality_check", DbQualityCheckNode.create())
                    .addNode("api_contract_generator", ApiContractGeneratorNode.create())
                    
                    // 前后端并行生成及质检节点
                    // 1. 前端分支 (包含图片并发收集)
                    .addNode("image_plan", FullStackImagePlanNode.create())
                    .addNode("content_image_collector", ContentImageCollectorNode.create())
                    .addNode("illustration_collector", IllustrationCollectorNode.create())
                    .addNode("diagram_collector", DiagramCollectorNode.create())
                    .addNode("logo_collector", LogoCollectorNode.create())
                    .addNode("image_aggregator", ImageAggregatorNode.create())
                    .addNode("prompt_enhancer", FullStackPromptEnhancerNode.create())
                    .addNode("frontend_generator", FrontendCodeGeneratorNode.create())
                    .addNode("frontend_quality_check", FrontendQualityCheckNode.create())
                    
                    // 2. 后端分支
                    .addNode("backend_generator", BackendCodeGeneratorNode.create())
                    .addNode("backend_quality_check", BackendQualityCheckNode.create())
                    
                    .addNode("fullstack_convergence", FullStackConvergenceNode.create())
                    .addNode("project_builder", ProjectBuilderNode.create())

                    // 定义流程图边
                    .addEdge(START, "requirements_analysis")
                    .addEdge("requirements_analysis", "database_designer")
                    .addEdge("database_designer", "db_quality_check")

                    // 数据库质检条件路由
                    .addConditionalEdges("db_quality_check",
                            edge_async(this::routeDbQualityCheck),
                            Map.of(
                                    "pass", "api_contract_generator",
                                    "retry", "database_designer",
                                    "fail", END
                            ))

                    // API 契约生成后，扇出 (Fan-out) 到前后端并发生成
                    .addEdge("api_contract_generator", "image_plan")
                    .addEdge("api_contract_generator", "backend_generator")

                    // 前端分支：先进行并发图片收集，再生成代码
                    .addEdge("image_plan", "content_image_collector")
                    .addEdge("image_plan", "illustration_collector")
                    .addEdge("image_plan", "diagram_collector")
                    .addEdge("image_plan", "logo_collector")

                    .addEdge("content_image_collector", "image_aggregator")
                    .addEdge("illustration_collector", "image_aggregator")
                    .addEdge("diagram_collector", "image_aggregator")
                    .addEdge("logo_collector", "image_aggregator")

                    .addEdge("image_aggregator", "prompt_enhancer")
                    .addEdge("prompt_enhancer", "frontend_generator")
                    
                    .addEdge("frontend_generator", "frontend_quality_check")
                    .addConditionalEdges("frontend_quality_check",
                            edge_async(this::routeFrontendQualityCheck),
                            Map.of(
                                    "pass", "fullstack_convergence",
                                    "retry", "frontend_generator",
                                    "fail", "fullstack_convergence" // 即使最终失败，也进入汇聚节点结束
                            ))

                    // 后端分支
                    .addEdge("backend_generator", "backend_quality_check")
                    .addConditionalEdges("backend_quality_check",
                            edge_async(this::routeBackendQualityCheck),
                            Map.of(
                                    "pass", "fullstack_convergence",
                                    "retry", "backend_generator",
                                    "fail", "fullstack_convergence"
                            ))

                    // 汇聚节点后进入项目构建
                    .addEdge("fullstack_convergence", "project_builder")
                    .addEdge("project_builder", END)
                    
                    .compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "全栈工作流创建失败");
        }
    }

    private String routeDbQualityCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult result = context.getDbQualityResult();
        if (result != null && result.getIsValid()) {
            return "pass";
        }
        if (context.getDbDesignRetryCount() <= MAX_RETRIES) {
            return "retry";
        }
        log.error("数据库质检失败超过最大重试次数 {}", MAX_RETRIES);
        return "fail";
    }

    private String routeFrontendQualityCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult result = context.getFrontendQualityResult();
        if (result != null && result.getIsValid()) {
            return "pass";
        }
        if (context.getFrontendRetryCount() <= MAX_RETRIES) {
            return "retry";
        }
        log.error("前端代码质检失败超过最大重试次数 {}", MAX_RETRIES);
        return "fail";
    }

    private String routeBackendQualityCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult result = context.getBackendQualityResult();
        if (result != null && result.getIsValid()) {
            return "pass";
        }
        if (context.getBackendRetryCount() <= MAX_RETRIES) {
            return "retry";
        }
        log.error("后端代码质检失败超过最大重试次数 {}", MAX_RETRIES);
        return "fail";
    }

    public Flux<String> executeWorkflowWithFlux(String originalPrompt, Long appId) {
        return Flux.create(sink -> {
            Thread.startVirtualThread(() -> {
                try {
                    CompiledGraph<MessagesState<String>> workflow = createWorkflow();
                    WorkflowContext initialContext = WorkflowContext.builder()
                            .originalPrompt(originalPrompt)
                            .appId(appId)
                            .currentStep("初始化")
                            .tokenEmitter(sink::next)
                            .build();

                    log.info("开始执行全栈代码生成工作流 - Flux流式输出");
                    sink.next("🚀 **开始执行全栈 Agent 模式代码生成** \n\n");
                    sink.next("💭 **思考过程：**正在启动全栈自动化流程... \n\n");

                    // 配置并发执行器
                    ExecutorService pool = ExecutorBuilder.create()
                            .setCorePoolSize(5)
                            .setMaxPoolSize(10)
                            .setWorkQueue(new LinkedBlockingQueue<>(100))
                            .setThreadFactory(ThreadFactoryBuilder.create().setNamePrefix("FullStack-Parallel").build())
                            .build();
                            
                    RunnableConfig runnableConfig = RunnableConfig.builder()
                            .addParallelNodeExecutor("api_contract_generator", pool) // 在扇出点设置并发执行器 (拆分前后端)
                            .addParallelNodeExecutor("image_plan", pool) // 在前端分支的扇出点设置并发执行器 (拆分四种图片收集)
                            .build();

                    for (NodeOutput<MessagesState<String>> step : workflow.stream(
                            Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext),
                            runnableConfig)) {
                        WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                        if (currentContext != null) {
                            sink.next(String.format("- ✅ **当前阶段**：%s \n\n", currentContext.getCurrentStep()));
                        }
                    }

                    sink.next("✅ **全栈代码生成工作流执行完毕！** \n");
                    sink.complete();
                    log.info("全栈代码生成工作流执行完成！");

                } catch (Exception e) {
                    log.error("全栈工作流执行失败", e);
                    sink.next("❌ **执行失败：** " + e.getMessage() + " \n");
                    sink.error(e);
                }
            });
        });
    }
}
