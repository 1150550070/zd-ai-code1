# 全栈项目自动化生成升级计划

为了支持从前端项目自动化生成升级为**全栈项目自动化生成**，系统架构需要引入新的 AI 节点和并发流程。整体设计遵循您要求的流程：需求分析 -> 数据库设计 -> 数据库质检 -> API 契约生成 -> (前后端并行生成) -> (前后端独立质检) -> 汇集构建。

## User Review Required

> [!IMPORTANT]
> - 本计划将引入数个新的 Node 和 AI Service。
> - 工作流需要处理前/后端独立生成和独立质检后的合并（Fan-in），这意味着我们需要修改状态机中的某些条件路由逻辑。
> - 请查看下方的流程设计，确认逻辑是否符合您的预期，特别是在某个分支（如前端）质检失败重试时，是否需要挂起另一端的构建。

## Open Questions

> [!WARNING]
> 1. 数据库设计质检和前后端质检失败后，最大重试次数是否有要求？（为防止无限循环，建议设置最大重试阈值，如3次）。
> 2. API契约生成的具体格式是否有特殊要求（例如 OpenAPI/Swagger 规范、JSON 格式等）？

## Proposed Changes

### 工作流模型层 (State/Model)

#### [MODIFY] [WorkflowContext.java](file:///d:/JAVA-basic-code/zd-ai-code/zd-ai-code/zd-ai-code/src/main/java/com/sht/zdaicode/langgraph4j/state/WorkflowContext.java)
增加全栈生命周期中各个节点的状态字段，包括但不限于：
- `analyzedRequirements`: (String) 需求分析后输出的详细需求结果
- `apiContract`: (String) API 接口契约
- `dbQualityResult`: (QualityResult) 数据库设计的质检结果
- `frontendQualityResult`: (QualityResult) 前端代码的质检结果
- `backendQualityResult`: (QualityResult) 后端代码的质检结果
- （注：需保存前端和后端的分别生成代码路径，以便分离质检，可能需要扩充 `frontendGeneratedCodeDir` 和 `backendGeneratedCodeDir`）

### AI 服务层 (AI Services)

#### [NEW] [RequirementsAnalysisAiService.java](file:///d:/JAVA-basic-code/zd-ai-code/zd-ai-code/zd-ai-code/src/main/java/com/sht/zdaicode/ai/RequirementsAnalysisAiService.java) & Factory
用于接收用户原始输入，并输出丰富后的结构化需求说明。

#### [NEW] [ApiContractAiService.java](file:///d:/JAVA-basic-code/zd-ai-code/zd-ai-code/zd-ai-code/src/main/java/com/sht/zdaicode/ai/ApiContractAiService.java) & Factory
基于数据库 Schema 和需求分析，生成标准的 API 接口契约文档。

### 工作流节点层 (Nodes)

#### [NEW] [RequirementsAnalysisNode.java](file:///d:/JAVA-basic-code/zd-ai-code/zd-ai-code/zd-ai-code/src/main/java/com/sht/zdaicode/langgraph4j/node/RequirementsAnalysisNode.java)
调用 `RequirementsAnalysisAiService`，丰富用户初始需求，并存入上下文。

#### [NEW] [DbQualityCheckNode.java](file:///d:/JAVA-basic-code/zd-ai-code/zd-ai-code/zd-ai-code/src/main/java/com/sht/zdaicode/langgraph4j/node/DbQualityCheckNode.java)
分析生成的 `ProjectScheam` 和 DDL 语句的正确性。

#### [NEW] [ApiContractGeneratorNode.java](file:///d:/JAVA-basic-code/zd-ai-code/zd-ai-code/zd-ai-code/src/main/java/com/sht/zdaicode/langgraph4j/node/ApiContractGeneratorNode.java)
如果数据库质检通过，则负责生成 API 契约内容。

#### [NEW] [FrontendQualityCheckNode.java](file:///d:/JAVA-basic-code/zd-ai-code/zd-ai-code/zd-ai-code/src/main/java/com/sht/zdaicode/langgraph4j/node/FrontendQualityCheckNode.java)
专门用于对生成的前端代码进行质量检查。

#### [NEW] [BackendQualityCheckNode.java](file:///d:/JAVA-basic-code/zd-ai-code/zd-ai-code/zd-ai-code/src/main/java/com/sht/zdaicode/langgraph4j/node/BackendQualityCheckNode.java)
专门用于对生成的后端代码进行质量检查。

#### [NEW] [FullStackConvergenceNode.java](file:///d:/JAVA-basic-code/zd-ai-code/zd-ai-code/zd-ai-code/src/main/java/com/sht/zdaicode/langgraph4j/node/FullStackConvergenceNode.java)
作为一个虚拟的等待/合并节点，确保在前后端均质检通过后，汇聚执行 `project_builder`。

### 工作流编排层 (Workflow)

#### [NEW] [FullStackCodeGenWorkflow.java](file:///d:/JAVA-basic-code/zd-ai-code/zd-ai-code/zd-ai-code/src/main/java/com/sht/zdaicode/langgraph4j/FullStackCodeGenWorkflow.java)
使用 LangGraph4j 重新编排新的全栈流程：
```text
START
  │
  ▼
需求分析 (RequirementsAnalysisNode)
  │
  ▼
数据库设计 (DatabaseDesignerNode) <──────┐
  │                                     │(重试)
  ▼                                     │
DB质检 (DbQualityCheckNode) ──[不合格]──┘
  │[合格]
  ▼
API契约生成 (ApiContractGeneratorNode)
  │
  ├──并发──> 前端生成 (FrontendCodeGeneratorNode) <─────┐
  │              │                                     │(重试)
  │              ▼                                     │
  │          前端质检 (FrontendQualityCheckNode) ──[失败]─┘
  │              │[成功]
  │              ▼
  │
  └──并发──> 后端生成 (BackendCodeGeneratorNode) <──────┐
                 │                                     │(重试)
                 ▼                                     │
             后端质检 (BackendQualityCheckNode) ──[失败]─┘
                 │[成功]
                 ▼
  ┌──────────────┴──────────────┐
  │        (等待前后端都完成)         │
  ▼                             ▼
  └────────> 汇集构建 (ProjectBuilderNode)
                                │
                                ▼
                               END
```
为实现上述的并发同步，需要设置特殊的路由控制或采用自定义 State (如携带 `frontend_done` 和 `backend_done` 标志位) 并在 Convergence 节点中检查。

## Verification Plan
### Automated Tests
无需直接编写单元测试，但将通过编译所有的工厂类及 Workflow 类来确保 Java 语法和类的依赖性准确无误。
### Manual Verification
1. 将手动启动或由您调用生成的流式输出接口。
2. 验证各个节点日志，检查“需求分析 -> 数据库设计 -> DB质检 -> API生成”环节是否按顺序执行。
3. 验证“前端生成 -> 质检”与“后端生成 -> 质检”是否并发执行且能汇聚构建。
