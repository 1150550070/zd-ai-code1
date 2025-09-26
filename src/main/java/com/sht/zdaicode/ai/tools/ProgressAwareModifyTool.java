package com.sht.zdaicode.ai.tools;

import cn.hutool.json.JSONObject;
import com.sht.zdaicode.ai.tools.progress.ProgressNotifier;
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
import java.util.UUID;

/**
 * 带进度反馈的文件修改工具
 * 适用于批量修改操作和复杂修改场景
 */
@Slf4j
@Component
public class ProgressAwareModifyTool extends BaseTool {

    @Resource
    private ProgressNotifier progressNotifier;

    @Tool("带进度反馈的文件修改，适用于批量操作")
    public String modifyFileWithProgress(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要替换的旧内容")
            String oldContent,
            @P("替换后的新内容")
            String newContent,
            @ToolMemoryId Long appId
    ) {
        String operationId = UUID.randomUUID().toString();
        
        try {
            progressNotifier.notifyStart(operationId, "开始修改文件: " + relativeFilePath);
            
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()) {
                String projectDirName = "vue_project_" + appId;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            
            progressNotifier.notifyProgress(operationId, 20, 100, "检查文件存在性...");
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                progressNotifier.notifyError(operationId, "文件不存在或不是文件");
                return "错误：文件不存在或不是文件 - " + relativeFilePath;
            }
            
            progressNotifier.notifyProgress(operationId, 40, 100, "读取原文件内容...");
            String originalContent = Files.readString(path);
            
            progressNotifier.notifyProgress(operationId, 60, 100, "检查替换内容...");
            if (!originalContent.contains(oldContent)) {
                progressNotifier.notifyError(operationId, "未找到要替换的内容");
                return "警告：文件中未找到要替换的内容，文件未修改 - " + relativeFilePath;
            }
            
            progressNotifier.notifyProgress(operationId, 80, 100, "执行内容替换...");
            String modifiedContent = originalContent.replace(oldContent, newContent);
            
            if (originalContent.equals(modifiedContent)) {
                progressNotifier.notifyComplete(operationId, "内容未发生变化");
                return "信息：替换后文件内容未发生变化 - " + relativeFilePath;
            }
            
            Files.writeString(path, modifiedContent);
            
            progressNotifier.notifyComplete(operationId, "文件修改完成: " + relativeFilePath);
            log.info("成功修改文件: {}", path.toAbsolutePath());
            
            return "文件修改成功: " + relativeFilePath;
            
        } catch (IOException e) {
            progressNotifier.notifyError(operationId, "文件操作失败: " + e.getMessage());
            String errorMessage = "修改文件失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "progressAwareModifyFile";
    }

    @Override
    public String getDisplayName() {
        return "进度感知修改文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String oldContent = arguments.getStr("oldContent");
        String newContent = arguments.getStr("newContent");
        
        // 对于长内容，进行截断显示
        String displayOldContent = oldContent.length() > 200 ? 
            oldContent.substring(0, 200) + "..." : oldContent;
        String displayNewContent = newContent.length() > 200 ? 
            newContent.substring(0, 200) + "..." : newContent;
        
        return String.format("""
                [工具调用] %s %s
                
                替换前：
                ```
                %s
                ```
                
                替换后：
                ```
                %s
                ```
                """, getDisplayName(), relativeFilePath, displayOldContent, displayNewContent);
    }
}