package com.sht.zdaicode.langgraph4j.node;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.sht.zdaicode.langgraph4j.ai.CodeQualityCheckService;
import com.sht.zdaicode.langgraph4j.model.QualityResult;
import com.sht.zdaicode.langgraph4j.state.WorkflowContext;
import com.sht.zdaicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class FrontendQualityCheckNode {

    private static final List<String> CODE_EXTENSIONS = Arrays.asList(".html", ".htm", ".css", ".js", ".json", ".vue", ".ts", ".jsx", ".tsx");

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 前端代码质量检查");
            String generatedCodeDir = context.getFrontendGeneratedCodeDir();
            if (StrUtil.isBlank(generatedCodeDir)) {
                generatedCodeDir = context.getGeneratedCodeDir(); // fallback
            }
            QualityResult qualityResult;
            try {
                String codeContent = readAndConcatenateCodeFiles(generatedCodeDir);
                if (StrUtil.isBlank(codeContent)) {
                    log.warn("未找到可检查的前端代码文件");
                    qualityResult = QualityResult.builder()
                            .isValid(false)
                            .errors(List.of("未找到可检查的前端代码文件"))
                            .suggestions(List.of("请确保前端代码生成成功"))
                            .build();
                } else {
                    if (context.getTokenEmitter() != null) {
                        context.getTokenEmitter().accept("\n> 🔍 正在进行前端代码全栈契约与功能质检...\n");
                    }
                    CodeQualityCheckService qualityCheckService = SpringContextUtil.getBean(CodeQualityCheckService.class);
                    
                    // 将精炼后的 Schema 加入到质检内容中 (剥离后端专属字段，减少质检上下文噪声)
                    String schemaJson = "";
                    if (context.getProjectSchema() != null) {
                        schemaJson = "\n\n【前端数据模型 (精炼后)】\n"
                                + cn.hutool.json.JSONUtil.toJsonStr(
                                    com.sht.zdaicode.ai.model.scheam.SchemaSlimmer.slim(context.getProjectSchema()));
                    }
                    
                    qualityResult = qualityCheckService.checkFullstackFrontendCodeQuality("请检查以下前端代码质量:\n" + codeContent + schemaJson);
                    log.info("前端代码全栈质量检查完成 - 是否通过: {}", qualityResult.getIsValid());
                }
            } catch (Exception e) {
                log.error("前端代码质量检查异常: {}", e.getMessage(), e);
                qualityResult = QualityResult.builder()
                        .isValid(false)
                        .errors(List.of(e.getMessage()))
                        .build();
            }

            if (!qualityResult.getIsValid()) {
                context.setFrontendRetryCount(context.getFrontendRetryCount() + 1);
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n> ⚠️ 前端代码质检未通过，进行重试 (已重试 " + context.getFrontendRetryCount() + " 次)\n");
                }
            } else {
                if (context.getTokenEmitter() != null) {
                    context.getTokenEmitter().accept("\n> ✅ 前端代码质检通过。\n");
                }
                context.setFrontendDone(true);
            }

            context.setFrontendQualityResult(qualityResult);
            context.setCurrentStep("前端代码质检");
            return WorkflowContext.saveContext(context);
        });
    }

    private static String readAndConcatenateCodeFiles(String codeDir) {
        if (StrUtil.isBlank(codeDir)) {
            return "";
        }
        File directory = new File(codeDir);
        if (!directory.exists() || !directory.isDirectory()) {
            return "";
        }
        StringBuilder codeContent = new StringBuilder();
        codeContent.append("# 前端项目结构和代码内容\n\n");
        FileUtil.walkFiles(directory, file -> {
            if (shouldSkipFile(file, directory)) return;
            if (isCodeFile(file)) {
                String relativePath = FileUtil.subPath(directory.getAbsolutePath(), file.getAbsolutePath());
                codeContent.append("## 文件: ").append(relativePath).append("\n\n");
                codeContent.append(FileUtil.readUtf8String(file)).append("\n\n");
            }
        });
        return codeContent.toString();
    }

    private static boolean shouldSkipFile(File file, File rootDir) {
        String relativePath = FileUtil.subPath(rootDir.getAbsolutePath(), file.getAbsolutePath());
        if (file.getName().startsWith(".")) return true;
        return relativePath.contains("node_modules" + File.separator) ||
                relativePath.contains("dist" + File.separator);
    }

    private static boolean isCodeFile(File file) {
        String fileName = file.getName().toLowerCase();
        return CODE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
}
