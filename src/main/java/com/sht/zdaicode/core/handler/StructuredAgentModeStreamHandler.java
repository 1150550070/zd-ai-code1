package com.sht.zdaicode.core.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sht.zdaicode.ai.model.message.ToolRequestMessage;
import com.sht.zdaicode.ai.tools.FileDirReadTool;
import com.sht.zdaicode.ai.tools.FileModifyTool;
import com.sht.zdaicode.ai.tools.FileReadTool;
import com.sht.zdaicode.model.dto.agent.AgentProgressResponse;
import com.sht.zdaicode.model.dto.agent.AgentStepResponse;
import com.sht.zdaicode.model.enums.ChatHistoryMessageTypeEnum;
import com.sht.zdaicode.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化Agent模式流处理器
 * 将Agent模式的输出转换为结构化的JSON格式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredAgentModeStreamHandler {

    private final ChatHistoryService chatHistoryService;
    private final ObjectMapper objectMapper;
    private final FileReadTool fileReadTool;
    private final FileDirReadTool fileDirReadTool;
    private final FileModifyTool fileModifyTool;

    // 匹配步骤的正则表达式
    private static final Pattern STEP_PATTERN = Pattern.compile("--- 第 (\\d+) 步.*?:(.*?) ---");
    private static final Pattern COMPLETION_PATTERN = Pattern.compile("--- 第 (\\d+) 步完成:(.*?) ---");

    /**
     * 处理工具调用（编辑模式：读取目录、读取文件、修改文件）
     */
    private void handleToolCallsInChunk(String chunk, Long appId, Long userId) {
        try {
            // 检查是否包含编辑模式的工具调用
            if (chunk.contains("read_file") || chunk.contains("list_files") ||
                    chunk.contains("replace_in_file") || chunk.contains("modifyFile") ||
                    chunk.contains("<read_file>") || chunk.contains("<list_files>") ||
                    chunk.contains("<replace_in_file>")) {

                log.info("检测到Agent模式编辑工具调用，开始处理");

                // 解析工具调用
                List<ToolRequestMessage> toolCalls = parseEditToolCallsFromChunk(chunk);
                if (!toolCalls.isEmpty()) {
                    for (ToolRequestMessage toolCall : toolCalls) {
                        executeEditToolCall(toolCall, appId, userId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理Agent模式编辑工具调用失败", e);
        }
    }

    /**
     * 执行编辑工具调用
     */
    private void executeEditToolCall(ToolRequestMessage toolCall, Long appId, Long userId) {
        try {
            String toolName = toolCall.getName();
            String arguments = toolCall.getArguments();

            log.info("执行编辑工具调用: toolName={}, arguments={}", toolName, arguments);

            switch (toolName) {
                case "readFile" -> {
                    // 解析参数并调用文件读取工具
                    Map<String, Object> params = parseArguments(arguments);
                    String path = (String) params.get("relativeFilePath");
                    String result = fileReadTool.readFile(path, appId);
                    log.info("文件读取完成: path={}, result length={}", path, result.length());
                }
                case "readDir" -> {
                    // 解析参数并调用目录读取工具
                    Map<String, Object> params = parseArguments(arguments);
                    String path = (String) params.get("relativeFilePath");
                    String result = fileDirReadTool.readDir(path, appId);
                    log.info("目录读取完成: path={}, result length={}", path, result.length());
                }
                case "modifyFile" -> {
                    // 解析参数并调用文件修改工具
                    Map<String, Object> params = parseArguments(arguments);
                    String path = (String) params.get("relativeFilePath");
                    String oldContent = (String) params.get("oldContent");
                    String newContent = (String) params.get("newContent");
                    String result = fileModifyTool.modifyFile(path, oldContent, newContent, appId);
                    log.info("文件修改完成: path={}, result={}", path, result);
                }
                default -> log.warn("不支持的编辑工具: {}", toolName);
            }
        } catch (Exception e) {
            log.error("执行编辑工具调用失败: toolName={}", toolCall.getName(), e);
        }
    }

    /**
     * 解析工具调用参数
     */
    private Map<String, Object> parseArguments(String arguments) {
        try {
            return objectMapper.readValue(arguments, Map.class);
        } catch (Exception e) {
            log.error("解析工具调用参数失败: {}", arguments, e);
            return Map.of();
        }
    }

    /**
     * 从输出块中解析编辑模式的工具调用
     */
    private List<ToolRequestMessage> parseEditToolCallsFromChunk(String chunk) {
        List<ToolRequestMessage> toolCalls = new ArrayList<>();

        try {
            // 解析 read_file 工具调用
            parseReadFileToolCall(chunk, toolCalls);

            // 解析 list_files 工具调用
            parseListFilesToolCall(chunk, toolCalls);

            // 解析 replace_in_file 工具调用
            parseReplaceInFileToolCall(chunk, toolCalls);

        } catch (Exception e) {
            log.error("解析编辑工具调用失败", e);
        }

        return toolCalls;
    }

    /**
     * 解析 read_file 工具调用
     */
    private void parseReadFileToolCall(String chunk, List<ToolRequestMessage> toolCalls) {
        Pattern pattern = Pattern.compile("<read_file[^>]*>.*?<path>(.*?)</path>.*?</read_file>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(chunk);

        while (matcher.find()) {
            String path = matcher.group(1).trim();

            ToolRequestMessage toolCall = new ToolRequestMessage();
            toolCall.setName("readFile");
            toolCall.setArguments("{\"relativeFilePath\":\"" + path + "\"}");

            toolCalls.add(toolCall);
            log.info("解析到read_file工具调用: path={}", path);
        }
    }

    /**
     * 解析 list_files 工具调用
     */
    private void parseListFilesToolCall(String chunk, List<ToolRequestMessage> toolCalls) {
        Pattern pattern = Pattern.compile("<list_files[^>]*>.*?<path>(.*?)</path>.*?</list_files>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(chunk);

        while (matcher.find()) {
            String path = matcher.group(1).trim();

            ToolRequestMessage toolCall = new ToolRequestMessage();
            toolCall.setName("readDir");
            toolCall.setArguments("{\"relativeFilePath\":\"" + path + "\"}");

            toolCalls.add(toolCall);
            log.info("解析到list_files工具调用: path={}", path);
        }
    }

    /**
     * 解析 replace_in_file 工具调用
     */
    private void parseReplaceInFileToolCall(String chunk, List<ToolRequestMessage> toolCalls) {
        Pattern pattern = Pattern.compile("<replace_in_file[^>]*>.*?<path>(.*?)</path>.*?<diff>(.*?)</diff>.*?</replace_in_file>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(chunk);

        while (matcher.find()) {
            String path = matcher.group(1).trim();
            String diff = matcher.group(2).trim();

            // 解析diff内容，提取SEARCH和REPLACE块
            String[] searchReplacePairs = parseDiffContent(diff);
            if (searchReplacePairs.length >= 2) {
                String oldContent = searchReplacePairs[0];
                String newContent = searchReplacePairs[1];

                ToolRequestMessage toolCall = new ToolRequestMessage();
                toolCall.setName("modifyFile");
                toolCall.setArguments(String.format("{\"relativeFilePath\":\"%s\",\"oldContent\":\"%s\",\"newContent\":\"%s\"}",
                        path, escapeJson(oldContent), escapeJson(newContent)));

                toolCalls.add(toolCall);
                log.info("解析到replace_in_file工具调用: path={}", path);
            }
        }
    }

    /**
     * 解析diff内容，提取SEARCH和REPLACE块
     */
    private String[] parseDiffContent(String diff) {
        try {
            // 查找SEARCH和REPLACE块
            Pattern searchPattern = Pattern.compile("------- SEARCH\\s*\\n(.*?)\\n=======", Pattern.DOTALL);
            Pattern replacePattern = Pattern.compile("=======\\s*\\n(.*?)\\n\\+\\+\\+\\+\\+\\+\\+ REPLACE", Pattern.DOTALL);

            Matcher searchMatcher = searchPattern.matcher(diff);
            Matcher replaceMatcher = replacePattern.matcher(diff);

            if (searchMatcher.find() && replaceMatcher.find()) {
                String searchContent = searchMatcher.group(1).trim();
                String replaceContent = replaceMatcher.group(1).trim();
                return new String[]{searchContent, replaceContent};
            }
        } catch (Exception e) {
            log.error("解析diff内容失败", e);
        }

        return new String[0];
    }

    /**
     * 转义JSON字符串
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }


    private AgentProgressResponse parseChunkToProgress(String chunk, List<AgentStepResponse> steps, int totalSteps) {
        if (chunk == null || chunk.trim().isEmpty()) {
            return null;
        }

        Matcher stepMatcher = STEP_PATTERN.matcher(chunk);
        if (stepMatcher.find()) {
            int stepNumber = Integer.parseInt(stepMatcher.group(1));
            String stepName = stepMatcher.group(2).trim();
            AgentStepResponse step = AgentStepResponse.builder()
                    .stepNumber(stepNumber)
                    .stepName(stepName)
                    .status("running")
                    .description("正在执行: " + stepName)
                    .timestamp(System.currentTimeMillis())
                    .build();
            updateOrAddStep(steps, step);
            return AgentProgressResponse.builder()
                    .type("step")
                    .totalSteps(totalSteps)
                    .currentStep(stepNumber)
                    .progressPercentage((stepNumber * 100) / totalSteps)
                    .currentStepInfo(step)
                    .allSteps(new ArrayList<>(steps))
                    .message(chunk) // 👉 完美配合前端：流式输出原始 chunk
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        Matcher completionMatcher = COMPLETION_PATTERN.matcher(chunk);
        if (completionMatcher.find()) {
            int stepNumber = Integer.parseInt(completionMatcher.group(1));
            String stepName = completionMatcher.group(2).trim();
            AgentStepResponse completedStep = findAndUpdateStep(steps, stepNumber, "completed", "✅ " + stepName + " 完成");
            return AgentProgressResponse.builder()
                    .type("progress")
                    .totalSteps(totalSteps)
                    .currentStep(stepNumber)
                    .progressPercentage((stepNumber * 100) / totalSteps)
                    .currentStepInfo(completedStep)
                    .allSteps(new ArrayList<>(steps))
                    .message(chunk) // 👉 完美配合前端：流式输出原始 chunk
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        if (chunk.contains("并发代码生成工作流执行完成")) {
            return AgentProgressResponse.builder()
                    .type("complete")
                    .totalSteps(totalSteps)
                    .currentStep(totalSteps)
                    .progressPercentage(100)
                    .isComplete(true)
                    .message(chunk)
                    .allSteps(new ArrayList<>(steps))
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        return null;
    }

    /**
     * 处理Agent模式的结构化流式输出
     */
    public Flux<String> handleStructuredAgentStream(Flux<String> sourceStream, Long appId, String userMessage, Long userId) {
        StringBuilder summaryBuilder = new StringBuilder();
        List<AgentStepResponse> steps = new ArrayList<>();
        int totalSteps = 9; // 根据工作流设定的总步骤数

        return sourceStream
                .map(chunk -> {
                    try {
                        // 处理工具调用（编辑模式）
                        handleToolCallsInChunk(chunk, appId, userId);

                        // 解析步骤信息
                        AgentProgressResponse progress = parseChunkToProgress(chunk, steps, totalSteps);

                        summaryBuilder.append(chunk);

                        // 返回结构化的JSON响应
                        if (progress != null) {
                            return chunk;
                        } else {
                            // 对于无法解析的内容，包装成消息类型
                            AgentProgressResponse messageResponse = AgentProgressResponse.builder()
                                    .type("message")
                                    .message(chunk)
                                    .timestamp(System.currentTimeMillis())
                                    .build();
                            return objectMapper.writeValueAsString(messageResponse) + "\n";
                        }

                    } catch (Exception e) {
                        log.error("解析Agent输出失败", e);
                        // 返回原始内容
                        return chunk;
                    }
                })
                .doOnComplete(() -> {
                    try {
                        // 1.保存用户消息
                        chatHistoryService.addChatMessage(appId, userMessage, ChatHistoryMessageTypeEnum.USER.getValue(), userId);

                        // 2. 核心改进：调用精简提炼算法，对几万字的原始对话流进行过滤，只提取关键路径与缩略代码
                        String optimizedSummary = compressAgentOutputForDatabase(summaryBuilder.toString());

                        // 3. 安全裁剪，防止单条消息在极端情况下撑爆数据库字段
                        if (optimizedSummary.length() > 4000) {
                            optimizedSummary = optimizedSummary.substring(0, 4000) + "\n\n...(由于代码与日志较长已进行安全截断，完整效果可看右侧网页预览)...";
                        }

                        // 4. 将高度提炼后的关键日志存入数据库
                        chatHistoryService.addChatMessage(appId, optimizedSummary, ChatHistoryMessageTypeEnum.AI.getValue(), userId);
                        log.info("Agent模式高提炼历史记录成功存入数据库！应用ID: {}, 用户ID: {}", appId, userId);
                    } catch (Exception e) {
                        log.error("保存Agent模式对话历史失败", e);
                    }
                })
                .doOnError(error -> {
                    try {
                        // 保存用户消息和错误信息
                        chatHistoryService.addChatMessage(appId, userMessage, ChatHistoryMessageTypeEnum.USER.getValue(), userId);
                        chatHistoryService.addChatMessage(appId, "Agent模式执行失败: " + error.getMessage(), ChatHistoryMessageTypeEnum.AI.getValue(), userId);
                    } catch (Exception e) {
                        log.error("保存Agent模式错误信息失败", e);
                    }
                });
    }

    /**
     * 🔴 响应核心提炼压缩算法：将巨长且包含XML标签的代码输出转换为极度轻量、漂亮的 Markdown 关键报告
     */
    private String compressAgentOutputForDatabase(String rawOutput) {
        if (rawOutput == null || rawOutput.trim().isEmpty()) {
            return "Agent 模式应用编辑完成";
        }

        String result = rawOutput;

        try {
            // 1. 提炼并压缩 <replace_in_file> (修改文件和大段 Diff 代码块)
            Pattern replacePattern = Pattern.compile("<replace_in_file[^>]*>\\s*<path>(.*?)</path>\\s*<diff>(.*?)</diff>\\s*</replace_in_file>", Pattern.DOTALL);
            Matcher replaceMatcher = replacePattern.matcher(result);
            StringBuffer sb1 = new StringBuffer();
            while (replaceMatcher.find()) {
                String path = replaceMatcher.group(1).trim();
                String diff = replaceMatcher.group(2).trim();

                // 将几百行的巨长 Diff 压缩，只提取前 5 行作为预览，其余用指引文案代替
                String briefDiff = getBriefContent(diff, 5,
                        "// ✂️ ... (此处省略大段后续代码修改) ...\n" +
                                "// 💡 提示：完整代码已自动写入项目，您可以通过点击右侧的【编辑模式】或上方【下载代码】实现精准代码查阅。");

                String replacement = String.format("\n🛠️ **自动修改文件**: `%s`\n```diff\n%s\n```\n", path, briefDiff);
                // 必须使用 quoteReplacement 防止 diff 里的 $ 或 \ 字符引发正则转义崩溃
                replaceMatcher.appendReplacement(sb1, Matcher.quoteReplacement(replacement));
            }
            replaceMatcher.appendTail(sb1);
            result = sb1.toString();

            // 2. 提炼 <read_file> 标签
            Pattern readPattern = Pattern.compile("<read_file[^>]*>\\s*<path>(.*?)</path>\\s*</read_file>", Pattern.DOTALL);
            Matcher readMatcher = readPattern.matcher(result);
            StringBuffer sb2 = new StringBuffer();
            while (readMatcher.find()) {
                String path = readMatcher.group(1).trim();
                String replacement = String.format("\n🔍 **读取并查阅文件**: `%s`\n", path);
                readMatcher.appendReplacement(sb2, Matcher.quoteReplacement(replacement));
            }
            readMatcher.appendTail(sb2);
            result = sb2.toString();

            // 3. 提炼 <list_files> 标签
            Pattern listPattern = Pattern.compile("<list_files[^>]*>\\s*<path>(.*?)</path>\\s*</list_files>", Pattern.DOTALL);
            Matcher listMatcher = listPattern.matcher(result);
            StringBuffer sb3 = new StringBuffer();
            while (listMatcher.find()) {
                String path = listMatcher.group(1).trim();
                String replacement = String.format("\n📂 **扫描分析目录**: `%s`\n", path);
                listMatcher.appendReplacement(sb3, Matcher.quoteReplacement(replacement));
            }
            listMatcher.appendTail(sb3);
            result = sb3.toString();

            // 4. 提炼大模型可能输出的普通原生 Markdown 代码块 (如 ```vue 等)
            Pattern codeBlockPattern = Pattern.compile("```(\\s*\\w+)?\\n([\\s\\S]*?)```", Pattern.DOTALL);
            Matcher codeBlockMatcher = codeBlockPattern.matcher(result);
            StringBuffer sb4 = new StringBuffer();
            while (codeBlockMatcher.find()) {
                String lang = codeBlockMatcher.group(1) != null ? codeBlockMatcher.group(1).trim() : "code";
                String codeContent = codeBlockMatcher.group(2);

                if (codeContent.length() > 150) {
                    String briefCode = getBriefContent(codeContent, 5,
                            "// ✂️ ... (其余大段生成代码已省略) ...\n" +
                                    "// 💡 提示：该相对路径文件的最新完整代码，可点击上方【下载代码】或在右侧预览窗进行查阅。");
                    String replacement = String.format("\n```%s\n%s\n```\n", lang, briefCode);
                    codeBlockMatcher.appendReplacement(sb4, Matcher.quoteReplacement(replacement));
                } else {
                    codeBlockMatcher.appendReplacement(sb4, Matcher.quoteReplacement(codeBlockMatcher.group(0)));
                }
            }
            codeBlockMatcher.appendTail(sb4);
            result = sb4.toString();

            // 5. 格式化硬核的工作流内置标识，使其成为优美的 Markdown 日志
            result = result.replaceAll("--- 第 (\\d+) 步.*?:(.*?) ---", "\n🚀 **第 $1 步**: $2\n");
            result = result.replaceAll("--- 第 (\\d+) 步完成:(.*?) ---", "\n✅ **第 $1 步完成**: $2\n");
            result = result.replaceAll("并发代码生成工作流执行完成", "\n🎉 **智能 Agent 模式工作流全部顺利执行完成！**\n");

        } catch (Exception e) {
            log.error("提炼Agent精简日志发生未知异常，降级保存原始响应前1000字", e);
            if (rawOutput.length() > 1000) {
                return rawOutput.substring(0, 1000) + "...(解析提炼失败，日志已部分截断)...";
            }
            return rawOutput;
        }

        return result.trim();
    }

    /**
     * 辅助工具：安全截取文本的前几行，并追加省略和提示文案
     */
    private String getBriefContent(String content, int maxLines, String omitTip) {
        if (content == null || content.isEmpty()) return "";
        String[] lines = content.split("\\r?\\n");
        if (lines.length <= maxLines) {
            return content;
        }
        StringBuilder brief = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            brief.append(lines[i]).append("\n");
        }
        brief.append(omitTip);
        return brief.toString();
    }




    /**
     * 更新或添加步骤
     */
    private void updateOrAddStep(List<AgentStepResponse> steps, AgentStepResponse newStep) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).getStepNumber().equals(newStep.getStepNumber())) {
                steps.set(i, newStep);
                return;
            }
        }
        steps.add(newStep);
    }

    /**
     * 查找并更新步骤状态
     */
    private AgentStepResponse findAndUpdateStep(List<AgentStepResponse> steps, int stepNumber, String
            status, String result) {
        for (AgentStepResponse step : steps) {
            if (step.getStepNumber() == stepNumber) {
                step.setStatus(status);
                step.setResult(result);
                step.setTimestamp(System.currentTimeMillis());
                return step;
            }
        }

        // 如果没找到，创建新的
        AgentStepResponse newStep = AgentStepResponse.builder()
                .stepNumber(stepNumber)
                .status(status)
                .result(result)
                .timestamp(System.currentTimeMillis())
                .build();
        steps.add(newStep);
        return newStep;
    }

    /**
     * 提取关键信息用于数据库存储
     */
    private String extractKeyInfo(String chunk) {
        String cleaned = chunk.replaceAll("[🚀💭✅❌🔧🎨📸🖼️📊🏷️🔗✨🛤️💻🔍🏗️⚙️🌐🎭📈🎯🔄⚡🔬🔨]", "")
                .replaceAll("\\*\\*", "")
                .replaceAll("\\n+", " ")
                .trim();

        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100) + "...";
        }

        return cleaned + " ";
    }
}

