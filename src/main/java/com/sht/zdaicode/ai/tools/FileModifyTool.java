package com.sht.zdaicode.ai.tools;

import cn.hutool.json.JSONObject;
import com.sht.zdaicode.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 文件修改工具
 * 策略：Vue项目采用增量修改，目录固定为 vue_project_create_{appId}
 */
@Slf4j
@Component
public class FileModifyTool extends BaseTool {

    @Tool("修改文件内容，用新内容替换指定的旧内容")
    public String modifyFile(
            @P("文件的相对路径") String relativeFilePath,
            @P("要替换的旧内容") String oldContent,
            @P("替换后的新内容") String newContent,
            @ToolMemoryId Long appId
    ) {
        try {
            Path path = Paths.get(relativeFilePath);
            // 核心修正：严格遵守统一的目录命名规则
            if (!path.isAbsolute()) {
                String projectDirName = "vue_project_create_" + appId;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path = projectRoot.resolve(relativeFilePath);
            }

            if (!Files.exists(path)) {
                return "错误：文件不存在 - " + relativeFilePath;
            }

            String originalContent = Files.readString(path);

            // 1. 严格匹配
            if (originalContent.contains(oldContent)) {
                String modifiedContent = originalContent.replace(oldContent, newContent);
                Files.writeString(path, modifiedContent, StandardOpenOption.TRUNCATE_EXISTING);
                log.info("严格匹配修改成功: {}", path);
                return "修改成功";
            }

            // 2. 宽松匹配 (增量修改的容错机制)
            // 忽略 Windows/Linux 换行符差异 (\r\n vs \n)
            String normOriginal = originalContent.replace("\r\n", "\n");
            String normOld = oldContent.replace("\r\n", "\n");

            if (normOriginal.contains(normOld)) {
                String modifiedContent = normOriginal.replace(normOld, newContent);
                Files.writeString(path, modifiedContent, StandardOpenOption.TRUNCATE_EXISTING);
                log.info("宽松匹配修改成功: {}", path);
                return "修改成功 (通过宽松匹配)";
            }

            log.warn("修改失败，内容未找到。文件: {}", path);
            return "错误：未找到要替换的内容，请确保旧内容与文件完全一致。";

        } catch (IOException e) {
            log.error("修改文件异常", e);
            return "系统错误: " + e.getMessage();
        }
    }

    @Override
    public String getToolName() {
        return "modifyFile";
    }

    @Override
    public String getDisplayName() {
        return "修改文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        // 用于前端展示修改前后的对比
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String oldContent = arguments.getStr("oldContent");
        String newContent = arguments.getStr("newContent");
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
                """, getDisplayName(), relativeFilePath, oldContent, newContent);
    }
}