package com.sht.zdaicode.ai.tools;

import cn.hutool.json.JSONObject;
import com.sht.zdaicode.ai.tools.progress.ProgressNotifier;
import com.sht.zdaicode.ai.tools.snapshot.ModifySnapshot;
import com.sht.zdaicode.ai.tools.snapshot.ModifySnapshotManager;
import com.sht.zdaicode.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * 增量修改工具
 * 支持多处修改、撤销操作和修改历史管理
 */
@Slf4j
@Component
public class IncrementalModifyTool extends BaseTool {

    @Resource
    private ProgressNotifier progressNotifier;
    
    @Resource
    private ModifySnapshotManager snapshotManager;

    @Tool("增量修改文件，支持多处修改和撤销操作")
    public String incrementalModify(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要替换的旧内容")
            String oldContent,
            @P("替换后的新内容")
            String newContent,
            @P("修改描述")
            String description,
            @ToolMemoryId Long appId
    ) {
        String operationId = UUID.randomUUID().toString();
        
        try {
            progressNotifier.notifyStart(operationId, "开始增量修改文件: " + relativeFilePath);
            
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()) {
                String projectDirName = "vue_project_" + appId;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                progressNotifier.notifyError(operationId, "文件不存在或不是文件");
                return "错误：文件不存在或不是文件 - " + relativeFilePath;
            }
            
            progressNotifier.notifyProgress(operationId, 25, 100, "读取原文件内容...");
            String originalContent = Files.readString(path);
            
            // 创建修改快照
            progressNotifier.notifyProgress(operationId, 50, 100, "创建修改快照...");
            ModifySnapshot snapshot = new ModifySnapshot(originalContent, oldContent, newContent, description);
            snapshotManager.saveSnapshot(appId, relativeFilePath, snapshot);
            
            // 执行修改
            progressNotifier.notifyProgress(operationId, 75, 100, "执行内容替换...");
            if (!originalContent.contains(oldContent)) {
                progressNotifier.notifyError(operationId, "未找到要替换的内容");
                return "警告：文件中未找到要替换的内容，文件未修改 - " + relativeFilePath;
            }
            
            String modifiedContent = originalContent.replace(oldContent, newContent);
            
            if (originalContent.equals(modifiedContent)) {
                progressNotifier.notifyComplete(operationId, "内容未发生变化");
                return "信息：替换后文件内容未发生变化 - " + relativeFilePath;
            }
            
            // 验证修改结果
            if (validateModification(originalContent, modifiedContent, oldContent, newContent)) {
                Files.writeString(path, modifiedContent);
                progressNotifier.notifyComplete(operationId, "增量修改完成: " + relativeFilePath);
                log.info("成功执行增量修改: {}, 描述: {}", path.toAbsolutePath(), description);
                return String.format("增量修改成功: %s - %s", relativeFilePath, description);
            } else {
                progressNotifier.notifyError(operationId, "修改验证失败");
                return "修改验证失败，操作已回滚 - " + relativeFilePath;
            }
            
        } catch (IOException e) {
            progressNotifier.notifyError(operationId, "文件操作失败: " + e.getMessage());
            String errorMessage = "增量修改失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Tool("撤销最近的文件修改")
    public String undoLastModify(
            @P("文件的相对路径")
            String relativeFilePath,
            @ToolMemoryId Long appId
    ) {
        String operationId = UUID.randomUUID().toString();
        
        try {
            progressNotifier.notifyStart(operationId, "开始撤销修改: " + relativeFilePath);
            
            ModifySnapshot lastSnapshot = snapshotManager.getLastSnapshot(appId, relativeFilePath);
            if (lastSnapshot == null) {
                progressNotifier.notifyError(operationId, "没有找到可撤销的修改记录");
                return "没有找到可撤销的修改记录 - " + relativeFilePath;
            }
            
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()) {
                String projectDirName = "vue_project_" + appId;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            
            progressNotifier.notifyProgress(operationId, 50, 100, "恢复文件内容...");
            Files.writeString(path, lastSnapshot.getOriginalContent());
            
            // 移除已撤销的快照
            snapshotManager.removeLastSnapshot(appId, relativeFilePath);
            
            progressNotifier.notifyComplete(operationId, "撤销修改完成: " + relativeFilePath);
            log.info("成功撤销最近的修改: {}", path.toAbsolutePath());
            return String.format("成功撤销最近的修改: %s - %s", relativeFilePath, lastSnapshot.getDescription());
            
        } catch (IOException e) {
            progressNotifier.notifyError(operationId, "撤销操作失败: " + e.getMessage());
            String errorMessage = "撤销修改失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Tool("查看文件修改历史")
    public String getModifyHistory(
            @P("文件的相对路径")
            String relativeFilePath,
            @ToolMemoryId Long appId
    ) {
        List<ModifySnapshot> history = snapshotManager.getModifyHistory(appId, relativeFilePath);
        
        if (history.isEmpty()) {
            return "文件没有修改历史记录 - " + relativeFilePath;
        }
        
        StringBuilder result = new StringBuilder();
        result.append("文件修改历史 - ").append(relativeFilePath).append(":\n");
        
        for (int i = 0; i < history.size(); i++) {
            ModifySnapshot snapshot = history.get(i);
            result.append(String.format("%d. %s (时间: %s)\n", 
                i + 1, snapshot.getDescription(), snapshot.getTimestamp()));
        }
        
        return result.toString();
    }

    /**
     * 验证修改结果
     */
    private boolean validateModification(String originalContent, String modifiedContent, 
                                       String oldContent, String newContent) {
        // 基本验证：确保替换确实发生了
        if (originalContent.equals(modifiedContent)) {
            return false;
        }
        
        // 验证新内容确实被添加
        if (!modifiedContent.contains(newContent)) {
            log.warn("修改验证失败：新内容未在修改后的文件中找到");
            return false;
        }
        
        // 验证旧内容确实被移除（如果不是部分替换）
        if (modifiedContent.contains(oldContent) && !oldContent.equals(newContent)) {
            log.warn("修改验证警告：旧内容仍存在于修改后的文件中");
        }
        
        return true;
    }

    @Override
    public String getToolName() {
        return "incrementalModifyFile";
    }

    @Override
    public String getDisplayName() {
        return "增量修改文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String oldContent = arguments.getStr("oldContent");
        String newContent = arguments.getStr("newContent");
        String description = arguments.getStr("description");
        
        return String.format("""
                [工具调用] %s %s - %s
                
                替换前：
                ```
                %s
                ```
                
                替换后：
                ```
                %s
                ```
                """, getDisplayName(), relativeFilePath, description, oldContent, newContent);
    }
}