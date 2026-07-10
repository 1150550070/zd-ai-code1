package com.sht.zdaicode.langgraph4j.state;

import com.sht.zdaicode.ai.model.scheam.ProjectScheam;
import com.sht.zdaicode.langgraph4j.model.ImageCollectionPlan;
import com.sht.zdaicode.langgraph4j.model.ImageResource;
import com.sht.zdaicode.langgraph4j.model.QualityResult;
import com.sht.zdaicode.model.enums.CodeGenTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 工作流上下文 - 存储所有状态信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext implements Serializable {

    /**
     * WorkflowContext 在 MessagesState 中的存储key
     */
    public static final String WORKFLOW_CONTEXT_KEY = "workflowContext";

    private transient Consumer<String> tokenEmitter = token -> {};

    /**
     * 当前执行步骤
     */
    private String currentStep;

    /**
     * 用户原始输入的提示词
     */
    private String originalPrompt;

    /**
     * 图片资源字符串
     */
    private String imageListStr;

    /**
     * 图片资源列表
     */
    private List<ImageResource> imageList;

    /**
     * 增强后的提示词
     */
    private String enhancedPrompt;

    /**
     * 代码生成类型 (单一工程模式下使用)
     */
    private CodeGenTypeEnum generationType;

    /**
     * 前端代码生成类型 (全栈模式下使用)
     */
    private CodeGenTypeEnum frontendGenerationType;

    /**
     * 后端代码生成类型 (全栈模式下使用)
     */
    private CodeGenTypeEnum backendGenerationType;

    /**
     * 生成的代码目录
     */
    private String generatedCodeDir;

    /**
     * 构建成功的目录
     */
    private String buildResultDir;

    /**
     * 生成的代码内容
     */
    private String generatedCode;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 质量检查结果
     */
    private QualityResult qualityResult;

    /**
     * 图片收集计划
     */
    private ImageCollectionPlan imageCollectionPlan;

    /**
     * 应用 ID
     */
    private Long appId = 0L;

    /**
     * 全栈项目的全局数据结构 (Step 1 产出的中间态)
     */
    private ProjectScheam projectSchema;

    /**
     * 自动生成的数据库初始化 SQL 脚本
     */
    private String databaseInitSql;

    /**
     * 后端代码临时生成目录
     */
    private String backendCodeDir;

    /**
     * 前端代码临时生成目录
     */
    private String frontendCodeDir;

    /**
     * 最终统一的全栈工程根目录
     */
    private String unifiedProjectDir;




    /**
     * 并发图片收集的中间结果字段
     */
    private List<ImageResource> contentImages;
    private List<ImageResource> illustrations;
    private List<ImageResource> diagrams;
    private List<ImageResource> logos;

    // ========== 全栈工作流新增字段 ==========
    
    /**
     * 需求分析结果
     */
    private String analyzedRequirements;
    
    /**
     * 数据库 Schema 对象
     */
    private com.sht.zdaicode.ai.model.scheam.ProjectScheam projectSchema;
    
    /**
     * 数据库初始化 SQL 语句
     */
    private String databaseInitSql;
    
    /**
     * API 契约内容
     */
    private com.sht.zdaicode.ai.model.ApiContract apiContract;
    
    /**
     * 数据库设计质检结果
     */
    private QualityResult dbQualityResult;

    /**
     * 前端代码质检结果
     */
    private QualityResult frontendQualityResult;

    /**
     * 后端代码质检结果
     */
    private QualityResult backendQualityResult;
    
    /**
     * 独立的前端代码生成目录
     */
    private String frontendGeneratedCodeDir;
    
    /**
     * 独立的后端代码生成目录
     */
    private String backendGeneratedCodeDir;


    private String unifiedProjectDir;


    
    // 重试控制字段
    @Builder.Default
    private int dbDesignRetryCount = 0;
    
    @Builder.Default
    private int frontendRetryCount = 0;
    
    @Builder.Default
    private int backendRetryCount = 0;
    
    @Builder.Default
    private boolean frontendDone = false;
    
    @Builder.Default
    private boolean backendDone = false;

    @Serial
    private static final long serialVersionUID = 1L;

    // ========== 上下文操作方法 ==========

    /**
     * 从 MessagesState 中获取 WorkflowContext
     */
    public static WorkflowContext getContext(MessagesState<String> state) {
        return (WorkflowContext) state.data().get(WORKFLOW_CONTEXT_KEY);
    }

    /**
     * 将 WorkflowContext 保存到 MessagesState 中
     */
    public static Map<String, Object> saveContext(WorkflowContext context) {
        return Map.of(WORKFLOW_CONTEXT_KEY, context);
    }
}
