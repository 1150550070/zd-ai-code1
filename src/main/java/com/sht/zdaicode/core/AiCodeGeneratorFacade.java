package com.sht.zdaicode.core;

import cn.hutool.json.JSONUtil;
import com.sht.zdaicode.ai.*;
import com.sht.zdaicode.ai.model.HtmlCodeResult;
import com.sht.zdaicode.ai.model.MultiFileCodeResult;
import com.sht.zdaicode.ai.model.message.AiResponseMessage;
import com.sht.zdaicode.ai.model.message.ToolExecutedMessage;
import com.sht.zdaicode.ai.model.message.ToolRequestMessage;
import com.sht.zdaicode.constant.AppConstant;
import com.sht.zdaicode.core.builder.VueProjectBuilder;
import com.sht.zdaicode.core.parser.CodeParserExecutor;
import com.sht.zdaicode.core.saver.CodeFileSaverExecutor;
import com.sht.zdaicode.exception.BusinessException;
import com.sht.zdaicode.exception.ErrorCode;
import com.sht.zdaicode.model.enums.CodeGenTypeEnum;
import com.sht.zdaicode.model.enums.VueProjectScenarioEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

import static com.sht.zdaicode.model.enums.VueProjectScenarioEnum.VUE_PROJECT_CREATE;
import static com.sht.zdaicode.model.enums.VueProjectScenarioEnum.VUE_PROJECT_EDIT;

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;
    @Resource
    private VueProjectAiServiceFactory vueProjectAiServiceFactory;
    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;
    @Resource
    private BackendProjectAiServiceFactory backendProjectAiServiceFactory;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
//    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
//        if (codeGenTypeEnum == null) {
//            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
//        }
//        //根据appId获取相应的Ai服务实例
//        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
//        return switch (codeGenTypeEnum) {
//            case HTML -> {
//                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
//                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
//            }
//            case MULTI_FILE -> {
//                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
//                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
//            }
//            default -> {
//                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
//                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
//            }
//        };
//    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }

        return switch (codeGenTypeEnum) {
            case HTML -> {
                //根据appId和生成类型获取相应的Ai服务实例
                AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                //根据appId和生成类型获取相应的Ai服务实例
                AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case FRONTEND_FULLSTACK_HTML -> {
                AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
                Flux<String> codeStream = aiCodeGeneratorService.generateFullStackHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case FRONTEND_FULLSTACK_MULTI_FILE -> {
                AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
                Flux<String> codeStream = aiCodeGeneratorService.generateFullStackMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case BACKEND_JAVA -> {
                BackendProjectAiService backendProjectAiService = backendProjectAiServiceFactory.getBackendProjectAiServiceWithSmartTools(appId, codeGenTypeEnum, userMessage);
                TokenStream tokenStream = backendProjectAiService.createBackendProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, appId, codeGenTypeEnum);
            }
            case VUE_PROJECT_CREATE, VUE_PROJECT_EDIT -> {
                // 检测Vue项目场景（创建/修改）
                //根据appId获取相应的Ai服务实例
                AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
                //根据用户需求智能选择Vue项目场景(创建模式/编辑模式)
                CodeGenTypeEnum vueProjectScenario = aiCodeGenTypeRoutingService.routeVueProjectScenario(userMessage);
                log.info("Vue项目场景检测结果: {} - {}", vueProjectScenario.getValue(), vueProjectScenario.getText());

                // 使用智能工具选择器获取Vue项目专用AI服务
                VueProjectAiService vueProjectAiService = vueProjectAiServiceFactory.getVueProjectAiServiceWithSmartTools(appId, vueProjectScenario, userMessage);
                
                // 根据场景调用不同的方法
                TokenStream tokenStream = switch (vueProjectScenario) {
                    case VUE_PROJECT_CREATE -> vueProjectAiService.createVueProjectCodeStream(appId, userMessage);
                    case VUE_PROJECT_EDIT -> vueProjectAiService.editVueProjectCodeStream(appId, userMessage);
                    default -> throw new IllegalStateException("Unexpected value: " + vueProjectScenario);
                };
                
                yield processTokenStream(tokenStream, appId, vueProjectScenario);
            }
            case FRONTEND_FULLSTACK_VUE -> {
                // 对于全栈 Vue，不再进行场景检测，直接使用创建全栈 Vue 项目的专用服务与提示词
                log.info("执行全栈 Vue 模式生成，跳过场景检测");
                VueProjectAiService vueProjectAiService = vueProjectAiServiceFactory.getVueProjectAiServiceWithSmartTools(appId, codeGenTypeEnum, userMessage);
                TokenStream tokenStream = vueProjectAiService.createFullStackVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, appId, codeGenTypeEnum);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId, CodeGenTypeEnum codeGenTypeEnum) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        sink.next(partialResponse);
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        sink.next(String.format("\n> 🛠️ **正在执行操作**: `%s`\n", toolExecutionRequest.name()));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        sink.next("\n> ✅ 操作执行完毕\n\n");
                    })
                    .onCompleteResponse((ChatResponse completeResponse) -> {
                        // 仅对纯前端模式（VUE_PROJECT_CREATE, VUE_PROJECT_EDIT）进行同步构建，全栈模式由后续节点处理
                        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT_CREATE || codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT_EDIT) {
                            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_create_" + appId;
                            vueProjectBuilder.buildProject(projectPath);
                        }
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        log.error("代码生成流式输出异常", error);
                        sink.error(error);
                    })
                    .start();
        });
    }


    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码片段
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            // 流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }
}