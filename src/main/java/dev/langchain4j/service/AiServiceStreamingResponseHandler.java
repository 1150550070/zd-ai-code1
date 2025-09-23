package dev.langchain4j.service;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Handles response from a language model for AI Service that is streamed token-by-token. Handles both regular (text)
 * responses and responses with the request to execute one or multiple tools.
 */
@Internal
class AiServiceStreamingResponseHandler implements StreamingChatResponseHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AiServiceStreamingResponseHandler.class);

    private final ChatExecutor chatExecutor;
    private final AiServiceContext context;
    private final Object memoryId;
    private final GuardrailRequestParams commonGuardrailParams;
    private final Object methodKey;

    private final Consumer<String> partialResponseHandler;
    private final BiConsumer<Integer, ToolExecutionRequest> partialToolExecutionRequestHandler;
    private final BiConsumer<Integer, ToolExecutionRequest> completeToolExecutionRequestHandler;
    private final Consumer<ToolExecution> toolExecutionHandler;
    private final Consumer<ChatResponse> completeResponseHandler;

    private final Consumer<Throwable> errorHandler;

    private final ChatMemory temporaryMemory;
    private final TokenUsage tokenUsage;

    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final List<String> responseBuffer = new ArrayList<>();
    private final boolean hasOutputGuardrails;
    private final Set<String> failedTools = new HashSet<>();

    AiServiceStreamingResponseHandler(
            ChatExecutor chatExecutor,
            AiServiceContext context,
            Object memoryId,
            Consumer<String> partialResponseHandler,
            BiConsumer<Integer, ToolExecutionRequest> partialToolExecutionRequestHandler,
            BiConsumer<Integer, ToolExecutionRequest> completeToolExecutionRequestHandler,
            Consumer<ToolExecution> toolExecutionHandler,
            Consumer<ChatResponse> completeResponseHandler,
            Consumer<Throwable> errorHandler,
            ChatMemory temporaryMemory,
            TokenUsage tokenUsage,
            List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors,
            GuardrailRequestParams commonGuardrailParams,
            Object methodKey) {
        this.chatExecutor = ensureNotNull(chatExecutor, "chatExecutor");
        this.context = ensureNotNull(context, "context");
        this.memoryId = ensureNotNull(memoryId, "memoryId");
        this.methodKey = methodKey;

        this.partialResponseHandler = ensureNotNull(partialResponseHandler, "partialResponseHandler");
        this.partialToolExecutionRequestHandler = partialToolExecutionRequestHandler;
        this.completeToolExecutionRequestHandler = completeToolExecutionRequestHandler;
        this.completeResponseHandler = completeResponseHandler;
        this.toolExecutionHandler = toolExecutionHandler;
        this.errorHandler = errorHandler;

        this.temporaryMemory = temporaryMemory;
        this.tokenUsage = ensureNotNull(tokenUsage, "tokenUsage");
        this.commonGuardrailParams = commonGuardrailParams;

        this.toolSpecifications = copy(toolSpecifications);
        this.toolExecutors = copy(toolExecutors);
        this.hasOutputGuardrails = context.guardrailService().hasOutputGuardrails(methodKey);
    }

    /**
     * 处理部分响应
     *
     * @param partialResponse 部分响应
     */
    @Override
    public void onPartialResponse(String partialResponse) {
        // If we're using output guardrails, then buffer the partial response until the guardrails have completed
        if (hasOutputGuardrails) {
            responseBuffer.add(partialResponse);
        } else {
            partialResponseHandler.accept(partialResponse);
        }
    }

    /**
     * 处理部分工具执行请求
     *
     * @param index                     工具执行请求索引
     * @param partialToolExecutionRequest 部分工具执行请求
     */
    @Override
    public void onPartialToolExecutionRequest(int index, ToolExecutionRequest partialToolExecutionRequest) {
        // If we're using output guardrails, then buffer the partial response until the guardrails have completed
        partialToolExecutionRequestHandler.accept(index, partialToolExecutionRequest);
    }

    /**
     * 处理完整响应
     *
     * @param completeResponse 完整响应
     */
    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        AiMessage aiMessage = completeResponse.aiMessage();
        addToMemory(aiMessage);

        if (aiMessage.hasToolExecutionRequests()) {
            // 执行所有工具并收集结果
            List<ToolExecutionResultMessage> toolResults = new ArrayList<>();
            
            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                String toolName = toolExecutionRequest.name();
                ToolExecutor toolExecutor = toolExecutors.get(toolName);
                
                if (toolExecutor == null) {
                    LOG.warn("Tool executor not found for tool: {}", toolName);
                    
                    // 检查是否已经失败过这个工具，防止无限循环
                    if (failedTools.contains(toolName)) {
                        LOG.error("Preventing infinite loop: tool '{}' has already failed. Stopping execution.", toolName);
                        String stopResult = "STOP: Tool '" + toolName + "' not found and has been attempted before. " +
                                "Available tools: " + String.join(", ", toolExecutors.keySet()) + 
                                ". Please use a different approach or correct tool name.";
                        ToolExecutionResultMessage stopMessage = 
                                ToolExecutionResultMessage.from(toolExecutionRequest, stopResult);
                        toolResults.add(stopMessage);
                        addToMemory(stopMessage);
                        
                        // 直接完成响应，不再继续工具调用链
                        if (completeResponseHandler != null) {
                            ChatResponse finalResponse = ChatResponse.builder()
                                    .aiMessage(AiMessage.from("Tool execution stopped due to repeated failures."))
                                    .metadata(completeResponse.metadata())
                                    .build();
                            completeResponseHandler.accept(finalResponse);
                        }
                        return;
                    }
                    
                    // 记录失败的工具
                    failedTools.add(toolName);
                    
                    String errorResult = "Error: Tool '" + toolName + "' not found. Available tools: " + 
                            String.join(", ", toolExecutors.keySet()) + 
                            ". Please use the correct tool name and do not retry the same invalid tool.";
                    ToolExecutionResultMessage errorMessage = 
                            ToolExecutionResultMessage.from(toolExecutionRequest, errorResult);
                    toolResults.add(errorMessage);
                    addToMemory(errorMessage);
                    continue;
                }
                
                try {
                    String toolExecutionResult = toolExecutor.execute(toolExecutionRequest, memoryId);
                    ToolExecutionResultMessage toolExecutionResultMessage =
                            ToolExecutionResultMessage.from(toolExecutionRequest, toolExecutionResult);
                    toolResults.add(toolExecutionResultMessage);
                    addToMemory(toolExecutionResultMessage);

                    if (toolExecutionHandler != null) {
                        ToolExecution toolExecution = ToolExecution.builder()
                                .request(toolExecutionRequest)
                                .result(toolExecutionResult)
                                .build();
                        toolExecutionHandler.accept(toolExecution);
                    }
                } catch (Exception e) {
                    LOG.error("Tool execution failed for tool: {}", toolName, e);
                    String errorResult = "Error executing tool '" + toolName + "': " + e.getMessage();
                    ToolExecutionResultMessage errorMessage = 
                            ToolExecutionResultMessage.from(toolExecutionRequest, errorResult);
                    toolResults.add(errorMessage);
                    addToMemory(errorMessage);
                }
            }

            // 验证消息序列完整性
            List<ChatMessage> messages = messagesToSend(memoryId);
            if (!validateMessageSequence(messages)) {
                LOG.error("Invalid message sequence detected. Messages: {}", 
                    messages.stream().map(m -> m.getClass().getSimpleName()).toList());
                // 尝试修复消息序列
                messages = repairMessageSequence(messages);
            }

            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolSpecifications)
                    .build();

            var handler = new AiServiceStreamingResponseHandler(
                    chatExecutor,
                    context,
                    memoryId,
                    partialResponseHandler,
                    partialToolExecutionRequestHandler,
                    completeToolExecutionRequestHandler,
                    toolExecutionHandler,
                    completeResponseHandler,
                    errorHandler,
                    temporaryMemory,
                    TokenUsage.sum(tokenUsage, completeResponse.metadata().tokenUsage()),
                    toolSpecifications,
                    toolExecutors,
                    commonGuardrailParams,
                    methodKey);

            context.streamingChatModel.chat(chatRequest, handler);
        } else {
            if (completeResponseHandler != null) {
                ChatResponse finalChatResponse = ChatResponse.builder()
                        .aiMessage(aiMessage)
                        .metadata(completeResponse.metadata().toBuilder()
                                .tokenUsage(tokenUsage.add(
                                        completeResponse.metadata().tokenUsage()))
                                .build())
                        .build();

                // Invoke output guardrails
                if (hasOutputGuardrails) {
                    if (commonGuardrailParams != null) {
                        var newCommonParams = GuardrailRequestParams.builder()
                                .chatMemory(getMemory())
                                .augmentationResult(commonGuardrailParams.augmentationResult())
                                .userMessageTemplate(commonGuardrailParams.userMessageTemplate())
                                .variables(commonGuardrailParams.variables())
                                .build();

                        var outputGuardrailParams = OutputGuardrailRequest.builder()
                                .responseFromLLM(finalChatResponse)
                                .chatExecutor(chatExecutor)
                                .requestParams(newCommonParams)
                                .build();

                        finalChatResponse =
                                context.guardrailService().executeGuardrails(methodKey, outputGuardrailParams);
                    }

                    // If we have output guardrails, we should process all of the partial responses first before
                    // completing
                    responseBuffer.forEach(partialResponseHandler::accept);
                    responseBuffer.clear();
                }

                // TODO should completeResponseHandler accept all ChatResponses that happened?
                completeResponseHandler.accept(finalChatResponse);
            }
        }
    }

    private ChatMemory getMemory() {
        return getMemory(memoryId);
    }

    private ChatMemory getMemory(Object memId) {
        return context.hasChatMemory() ? context.chatMemoryService.getOrCreateChatMemory(memoryId) : temporaryMemory;
    }

    private void addToMemory(ChatMessage chatMessage) {
        getMemory().add(chatMessage);
    }

    private List<ChatMessage> messagesToSend(Object memoryId) {
        return getMemory(memoryId).messages();
    }

    /**
     * 验证消息序列是否符合OpenAI API要求
     * OpenAI要求：assistant消息包含tool_calls后，必须紧跟对应的tool结果消息
     */
    private boolean validateMessageSequence(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return true;
        }

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                // 检查后续消息是否包含对应的工具结果
                List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
                int expectedToolResults = toolRequests.size();
                int foundToolResults = 0;
                
                // 检查后续消息中的工具结果
                for (int j = i + 1; j < messages.size() && j < i + 1 + expectedToolResults; j++) {
                    if (messages.get(j) instanceof ToolExecutionResultMessage) {
                        foundToolResults++;
                    } else {
                        break; // 工具结果消息必须连续
                    }
                }
                
                if (foundToolResults != expectedToolResults) {
                    LOG.warn("Message sequence validation failed: expected {} tool results, found {}", 
                            expectedToolResults, foundToolResults);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 修复消息序列，确保符合OpenAI API要求
     */
    private List<ChatMessage> repairMessageSequence(List<ChatMessage> messages) {
        List<ChatMessage> repairedMessages = new ArrayList<>();
        
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            repairedMessages.add(message);
            
            if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                // 确保每个工具调用都有对应的结果消息
                List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
                
                for (ToolExecutionRequest toolRequest : toolRequests) {
                    // 查找对应的工具结果消息
                    boolean foundResult = false;
                    for (int j = i + 1; j < messages.size(); j++) {
                        if (messages.get(j) instanceof ToolExecutionResultMessage toolResult) {
                            if (toolRequest.id().equals(toolResult.id())) {
                                foundResult = true;
                                break;
                            }
                        }
                    }
                    
                    // 如果没找到结果消息，创建一个默认的
                    if (!foundResult) {
                        LOG.warn("Creating missing tool result for tool call: {}", toolRequest.id());
                        ToolExecutionResultMessage missingResult = 
                                ToolExecutionResultMessage.from(toolRequest, "Tool execution result not found");
                        repairedMessages.add(missingResult);
                    }
                }
            }
        }
        
        LOG.info("Message sequence repaired: {} -> {} messages", messages.size(), repairedMessages.size());
        return repairedMessages;
    }

    @Override
    public void onError(Throwable error) {
        if (errorHandler != null) {
            try {
                errorHandler.accept(error);
            } catch (Exception e) {
                LOG.error("While handling the following error...", error);
                LOG.error("...the following error happened", e);
            }
        } else {
            LOG.warn("Ignored error", error);
        }
    }
}
