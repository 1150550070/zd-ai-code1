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
                        
                        // 收集关键信息用于数据库存储
                        if (chunk.contains("步骤") || chunk.contains("完成")) {
                            summaryBuilder.append(extractKeyInfo(chunk));
                        }
                        
                        // 返回结构化的JSON响应
                        if (progress != null) {
                            return objectMapper.writeValueAsString(progress) + "\n";
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
                        // 保存用户消息
                        chatHistoryService.addChatMessage(appId, userMessage, ChatHistoryMessageTypeEnum.USER.getValue(), userId);
                        
                        // 保存简化的助手响应摘要
                        String summary = summaryBuilder.toString();
                        if (summary.length() > 1000) {
                            summary = summary.substring(0, 1000) + "...";
                        }
                        
                        if (summary.isEmpty()) {
                            summary = "Agent模式编辑完成";
                        }
                        
                        chatHistoryService.addChatMessage(appId, summary, ChatHistoryMessageTypeEnum.AI.getValue(), userId);
                        log.info("Agent模式对话历史保存成功，应用ID: {}, 用户ID: {}", appId, userId);
                        
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
     * 解析输出块为进度信息
     */
    private AgentProgressResponse parseChunkToProgress(String chunk, List<AgentStepResponse> steps, int totalSteps) {
        if (chunk == null || chunk.trim().isEmpty()) {
            return null;
        }

        // 匹配步骤开始
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
            
            // 更新或添加步骤
            updateOrAddStep(steps, step);
            
            return AgentProgressResponse.builder()
                    .type("step")
                    .totalSteps(totalSteps)
                    .currentStep(stepNumber)
                    .progressPercentage((stepNumber * 100) / totalSteps)
                    .currentStepInfo(step)
                    .allSteps(new ArrayList<>(steps))
                    .message("开始执行第 " + stepNumber + " 步: " + stepName)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        // 匹配步骤完成
        Matcher completionMatcher = COMPLETION_PATTERN.matcher(chunk);
        if (completionMatcher.find()) {
            int stepNumber = Integer.parseInt(completionMatcher.group(1));
            String stepName = completionMatcher.group(2).trim();
            
            // 更新步骤状态为完成
            AgentStepResponse completedStep = findAndUpdateStep(steps, stepNumber, "completed", "✅ " + stepName + " 完成");
            
            return AgentProgressResponse.builder()
                    .type("progress")
                    .totalSteps(totalSteps)
                    .currentStep(stepNumber)
                    .progressPercentage((stepNumber * 100) / totalSteps)
                    .currentStepInfo(completedStep)
                    .allSteps(new ArrayList<>(steps))
                    .message("第 " + stepNumber + " 步完成: " + stepName)
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        // 检查是否是工作流完成
        if (chunk.contains("并发代码生成工作流执行完成")) {
            return AgentProgressResponse.builder()
                    .type("complete")
                    .totalSteps(totalSteps)
                    .currentStep(totalSteps)
                    .progressPercentage(100)
                    .isComplete(true)
                    .message("🎉 编辑工作流执行完成！")
                    .allSteps(new ArrayList<>(steps))
                    .timestamp(System.currentTimeMillis())
                    .build();
        }

        return null;
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
    private AgentStepResponse findAndUpdateStep(List<AgentStepResponse> steps, int stepNumber, String status, String result) {
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