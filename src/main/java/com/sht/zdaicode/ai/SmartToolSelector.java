package com.sht.zdaicode.ai;

import com.sht.zdaicode.ai.tools.ToolManager;
import com.sht.zdaicode.constant.AppConstant;
import com.sht.zdaicode.model.enums.CodeGenTypeEnum;
import com.sht.zdaicode.model.enums.VueProjectScenarioEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 智能工具选择器
 * 基于用户意图和项目状态智能选择最优工具集
 */
@Slf4j
@Component
public class SmartToolSelector {

    @Resource
    private ToolManager toolManager;

    /**
     * 目录操作相关关键词
     */
    private static final Pattern DIRECTORY_KEYWORDS = Pattern.compile(
            ".*(目录|文件夹|结构|查看|浏览|列表|tree).*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 删除操作相关关键词
     */
    private static final Pattern DELETE_KEYWORDS = Pattern.compile(
            ".*(删除|移除|去掉|清除|remove|delete).*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 批量操作相关关键词
     */
    private static final Pattern BATCH_KEYWORDS = Pattern.compile(
            ".*(批量|多个|所有|全部|一次性|同时).*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 复杂修改操作关键词
     */
    private static final Pattern COMPLEX_MODIFY_KEYWORDS = Pattern.compile(
            ".*(重构|优化|整理|调整布局|改进|升级).*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 基于用户意图和项目状态智能选择工具集
     *
     * @param scenario 场景类型
     * @param appId 应用ID
     * @param userMessage 用户消息
     * @return 优化后的工具列表
     */
    public List<Object> selectOptimalTools(CodeGenTypeEnum scenario, Long appId, String userMessage) {
        log.info("智能工具选择 - 场景: {}, appId: {}, 用户消息: {}", scenario.getText(), appId, userMessage);
        
        // 分析用户操作意图
        OperationIntent intent = analyzeUserIntent(userMessage, appId);
        log.debug("用户操作意图分析结果: {}", intent);

        return switch (scenario) {
            case VUE_PROJECT_CREATE  -> getCreateModeTools(intent);
            case VUE_PROJECT_EDIT -> getEditModeTools(intent, appId);
            default -> throw new IllegalStateException("Unexpected value: " + scenario);
        };
    }

    /**
     * 分析用户操作意图
     *
     * @param userMessage 用户消息
     * @param appId 应用ID
     * @return 操作意图对象
     */
    private OperationIntent analyzeUserIntent(String userMessage, Long appId) {
        OperationIntent intent = new OperationIntent();
        
        // 检测是否需要目录操作
        intent.setNeedsDirectoryOperation(DIRECTORY_KEYWORDS.matcher(userMessage).matches());
        
        // 检测是否需要删除操作
        intent.setNeedsFileDelete(DELETE_KEYWORDS.matcher(userMessage).matches());
        
        // 检测是否是批量操作
        intent.setBatchOperation(BATCH_KEYWORDS.matcher(userMessage).matches());
        
        // 检测是否是复杂修改
        intent.setComplexModification(COMPLEX_MODIFY_KEYWORDS.matcher(userMessage).matches());
        
        // 检测项目复杂度
        intent.setProjectComplexity(analyzeProjectComplexity(appId));
        
        return intent;
    }

    /**
     * 获取创建模式的工具集
     *
     * @param intent 操作意图
     * @return 工具列表
     */
    private List<Object> getCreateModeTools(OperationIntent intent) {
        List<Object> tools = new ArrayList<>();
        
        // 基础创建工具
        tools.add(toolManager.getTool("writeFile"));
        
        // 如果是批量操作，添加进度反馈工具
        if (intent.isBatchOperation()) {
            tools.add(toolManager.getTool("progressAwareWriteFile"));
            log.debug("添加进度感知写入工具用于批量创建");
        }
        
        log.info("创建模式工具集配置完成，工具数量: {}", tools.size());
        return tools;
    }

    /**
     * 获取编辑模式的工具集
     *
     * @param intent 操作意图
     * @param appId 应用ID
     * @return 工具列表
     */
    private List<Object> getEditModeTools(OperationIntent intent, Long appId) {
        List<Object> tools = new ArrayList<>();
        
        // 基础编辑工具
        tools.add(toolManager.getTool("readFile"));
        tools.add(toolManager.getTool("modifyFile"));
        
        // 根据意图添加额外工具
        if (intent.isNeedsDirectoryOperation()) {
            tools.add(toolManager.getTool("readDir"));
            log.debug("添加目录读取工具");
        }
        
        if (intent.isNeedsFileDelete()) {
            tools.add(toolManager.getTool("deleteFile"));
            log.debug("添加文件删除工具");
        }
        
        // 复杂项目或复杂修改添加增强工具
        if (intent.getProjectComplexity() == ProjectComplexity.HIGH || intent.isComplexModification()) {
            tools.add(toolManager.getTool("incrementalModifyFile"));
            log.debug("添加增量修改工具用于复杂操作");
        }
        
        // 批量操作添加进度反馈
        if (intent.isBatchOperation()) {
            tools.add(toolManager.getTool("progressAwareModifyFile"));
            log.debug("添加进度感知修改工具用于批量操作");
        }
        
        log.info("编辑模式工具集配置完成，工具数量: {}", tools.size());
        return tools;
    }

    /**
     * 分析项目复杂度
     *
     * @param appId 应用ID
     * @return 项目复杂度
     */
    private ProjectComplexity analyzeProjectComplexity(Long appId) {
        String projectDirName = "vue_project_" + appId;
        String projectDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + projectDirName;
        File projectDir = new File(projectDirPath);
        
        if (!projectDir.exists()) {
            return ProjectComplexity.LOW;
        }
        
        // 统计文件数量
        int fileCount = countFiles(projectDir);
        
        if (fileCount > 50) {
            log.debug("项目复杂度: HIGH (文件数量: {})", fileCount);
            return ProjectComplexity.HIGH;
        } else if (fileCount > 20) {
            log.debug("项目复杂度: MEDIUM (文件数量: {})", fileCount);
            return ProjectComplexity.MEDIUM;
        } else {
            log.debug("项目复杂度: LOW (文件数量: {})", fileCount);
            return ProjectComplexity.LOW;
        }
    }

    /**
     * 递归统计文件数量
     *
     * @param dir 目录
     * @return 文件数量
     */
    private int countFiles(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    count++;
                } else if (file.isDirectory() && !file.getName().equals("node_modules")) {
                    count += countFiles(file);
                }
            }
        }
        return count;
    }

    /**
     * 操作意图内部类
     */
    public static class OperationIntent {
        private boolean needsDirectoryOperation = false;
        private boolean needsFileDelete = false;
        private boolean batchOperation = false;
        private boolean complexModification = false;
        private ProjectComplexity projectComplexity = ProjectComplexity.LOW;

        // Getters and Setters
        public boolean isNeedsDirectoryOperation() {
            return needsDirectoryOperation;
        }

        public void setNeedsDirectoryOperation(boolean needsDirectoryOperation) {
            this.needsDirectoryOperation = needsDirectoryOperation;
        }

        public boolean isNeedsFileDelete() {
            return needsFileDelete;
        }

        public void setNeedsFileDelete(boolean needsFileDelete) {
            this.needsFileDelete = needsFileDelete;
        }

        public boolean isBatchOperation() {
            return batchOperation;
        }

        public void setBatchOperation(boolean batchOperation) {
            this.batchOperation = batchOperation;
        }

        public boolean isComplexModification() {
            return complexModification;
        }

        public void setComplexModification(boolean complexModification) {
            this.complexModification = complexModification;
        }

        public ProjectComplexity getProjectComplexity() {
            return projectComplexity;
        }

        public void setProjectComplexity(ProjectComplexity projectComplexity) {
            this.projectComplexity = projectComplexity;
        }

        @Override
        public String toString() {
            return String.format("OperationIntent{needsDir=%s, needsDelete=%s, batch=%s, complex=%s, complexity=%s}",
                    needsDirectoryOperation, needsFileDelete, batchOperation, complexModification, projectComplexity);
        }
    }

    /**
     * 项目复杂度枚举
     */
    public enum ProjectComplexity {
        LOW, MEDIUM, HIGH
    }
}