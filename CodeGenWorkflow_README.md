# CodeGenConcurrentWorkflow 并发代码生成工作流

## 1. 简介
`CodeGenConcurrentWorkflow` 是本项目基于 `langgraph4j` 框架构建的**Agent多智能体并发工作流**。该工作流主要用于解析用户需求、并发收集所需静态资源，并最终由AI生成高质量代码。通过有向无环图（StateGraph）机制，将代码生成任务拆分为多个智能节点（Node），其中对于外部资源（如图片素材）的搜索和收集采用并发执行策略，大幅提升了系统的响应速度和整体执行效率。

## 2. 工作流结构与流程说明

该工作流结合了**串行**与**并发**两种执行模式。

### 2.1 节点流程图 (简化版)
```text
[START] -> 图片规划(image_plan) 
                ├─并发─> 内容图片收集 (content_image_collector) ─┐
                ├─并发─> 插图收集 (illustration_collector) ────┼─汇聚─> 图片聚合 (image_aggregator)
                ├─并发─> 图表收集 (diagram_collector) ─────────┤
                └─并发─> Logo收集 (logo_collector) ────────────┘
                                                                 ↓
                                                           提示词增强 (prompt_enhancer)
                                                                 ↓
                                                             智能路由 (router)
                                                                 ↓
  ┌────────────────────────────────────────────────────────<── 代码生成 (code_generator)
  │                                                              ↓
  └─(失败重试)──<── 代码质量检查 (code_quality_check) ──(条件路由)─┤
                                                                 ├─(通过且需构建)─> 项目构建 (project_builder) ──> [END]
                                                                 └─(通过且无需构建)─> [END]
```

### 2.2 核心节点说明
1. **图片规划 (`image_plan`)**：分析用户的初始项目需求，制定图片收集的分类与策略。
2. **并发收集节点**：
   - `content_image_collector`：并发搜索相关的业务内容图片资源。
   - `illustration_collector`：并发收集装饰性插图。
   - `diagram_collector`：并发获取数据可视化图表。
   - `logo_collector`：并发搜索品牌标识。
3. **图片聚合 (`image_aggregator`)**：所有并发收集任务完成后汇聚于此，将素材整合。
4. **提示词增强 (`prompt_enhancer`)**：将收集到的图片素材等资源信息，作为上下文融入代码生成的提示词中，增强 Prompt 质量。
5. **智能路由 (`router`)**：根据需求复杂度或类型，智能决策并路由到最适合的代码生成策略。
6. **代码生成 (`code_generator`)**：调用大语言模型（LLM）执行实际的代码生成。
7. **代码质量检查 (`code_quality_check`)**：自动对生成的代码进行质量与规范检查，决定后续走向：
   - 若不合格：回退至 `code_generator` 重新生成（自动纠错）。
   - 若合格且为Vue项目等需构建的工程：进入 `project_builder`。
   - 若合格且为单文件等无需构建场景：直接结束（`skip_build`）。
8. **项目构建 (`project_builder`)**：将生成的代码组装成完整的项目结构。

## 3. 核心功能特性

### 3.1 响应式流式输出 (Flux Streaming)
提供 `executeWorkflowWithFlux` 方法，利用 **Project Reactor (Flux)** 和 **Java 虚拟线程 (VirtualThread)** 实现了流式输出能力。
- 在后台异步执行复杂的图流转。
- 将每个节点的执行状态、思考过程、阶段性日志实时推送到前端。
- 天然契合 Server-Sent Events (SSE)，为用户提供“所见即所得”的 Agent 思考渲染效果。

### 3.2 细粒度的并发控制
- 引入了 `Hutool` 的线程池构建器，定制化配置了专门用于图片收集的并发执行器（核心线程10，最大线程20）。
- 在 `RunnableConfig` 中指定了 `image_plan` 的并行执行配置，突破了传统 LangChain/LangGraph 串行执行的性能瓶颈。

### 3.3 自动纠错与条件路由 (Conditional Edges)
- 基于 `addConditionalEdges` 实现了动态工作流。
- 当代码质量检查 (`code_quality_check`) 返回 `fail` 标识时，流程可动态回滚并重试，体现了 Agent 闭环反馈和自我修正的高级特性。

## 4. 技术栈依赖
- **[LangGraph4j](https://github.com/bsc-langgraph4j/langgraph4j)**：作为底层图工作流编排引擎。
- **Project Reactor** (`Flux`)：支持非阻塞的流式响应。
- **Hutool**：提供便捷的线程池构建支持。
- **Java 21+**（预估）：采用了 `Thread.startVirtualThread` 虚拟线程特性。
