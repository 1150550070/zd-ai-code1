package com.sht.zdaicode.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import com.sht.zdaicode.ai.tools.progress.ProgressNotifier;
import com.sht.zdaicode.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 带进度反馈的文件写入工具
 * 支持大文件分块写入和实时进度反馈
 */
@Slf4j
@Component
public class ProgressAwareFileWriteTool extends BaseTool {

    @Resource
    private ProgressNotifier progressNotifier;

    /**
     * 大文件阈值（字符数）
     */
    private static final int LARGE_FILE_THRESHOLD = 10000;

    /**
     * 分块大小（字符数）
     */
    private static final int CHUNK_SIZE = 1000;

    @Tool("带进度反馈的文件写入，适用于大文件或批量操作")
    public String writeFileWithProgress(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要写入文件的内容")
            String content,
            @ToolMemoryId Long appId
    ) {
        String operationId = UUID.randomUUID().toString();
        
        try {
            // 发送开始信号
            progressNotifier.notifyStart(operationId, "开始写入文件: " + relativeFilePath);
            
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()) {
                String projectDirName = "vue_project_create_" + appId;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            
            // 创建父目录
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            
            // 根据文件大小选择写入策略
            if (content.length() > LARGE_FILE_THRESHOLD) {
                return writeFileInChunks(path, content, operationId, relativeFilePath);
            } else {
                return writeFileDirectly(path, content, operationId, relativeFilePath);
            }
            
        } catch (Exception e) {
            progressNotifier.notifyError(operationId, "文件写入失败: " + e.getMessage());
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    /**
     * 直接写入小文件
     */
    private String writeFileDirectly(Path path, String content, String operationId, String relativeFilePath) throws IOException {
        progressNotifier.notifyProgress(operationId, 50, 100, "正在写入文件内容...");
        
        Files.writeString(path, content);
        
        progressNotifier.notifyComplete(operationId, "文件写入完成: " + relativeFilePath);
        log.info("成功写入文件: {}", path.toAbsolutePath());
        
        return "文件写入成功: " + relativeFilePath;
    }

    /**
     * 分块写入大文件
     */
    private String writeFileInChunks(Path path, String content, String operationId, String relativeFilePath) throws IOException {
        int totalChunks = (content.length() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        
        progressNotifier.notifyProgress(operationId, 0, totalChunks, "开始分块写入大文件...");
        
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (int i = 0; i < totalChunks; i++) {
                int start = i * CHUNK_SIZE;
                int end = Math.min(start + CHUNK_SIZE, content.length());
                writer.write(content.substring(start, end));
                
                // 发送进度更新
                progressNotifier.notifyProgress(operationId, i + 1, totalChunks, 
                    String.format("已写入 %d/%d 块", i + 1, totalChunks));
                
                // 模拟写入延迟，让用户能看到进度
                if (totalChunks > 10) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        progressNotifier.notifyComplete(operationId, "大文件写入完成: " + relativeFilePath);
        log.info("成功分块写入大文件: {}, 总块数: {}", path.toAbsolutePath(), totalChunks);
        
        return String.format("大文件写入成功: %s (共 %d 块)", relativeFilePath, totalChunks);
    }

    @Override
    public String getToolName() {
        return "progressAwareWriteFile";
    }

    @Override
    public String getDisplayName() {
        return "进度感知写入文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String suffix = FileUtil.getSuffix(relativeFilePath);
        String content = arguments.getStr("content");
        
        // 对于大文件，只显示部分内容
        String displayContent = content;
        if (content.length() > 500) {
            displayContent = content.substring(0, 500) + "\n... (内容过长，已截断)";
        }
        
        return String.format("""
                [工具调用] %s %s
                ```%s
                %s
                ```
                """, getDisplayName(), relativeFilePath, suffix, displayContent);
    }
}