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
                AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT_CREATE, VUE_PROJECT_EDIT -> {
                AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
                CodeGenTypeEnum vueProjectScenario = aiCodeGenTypeRoutingService.routeVueProjectScenario(userMessage);
                log.info("Vue项目场景检测结果: {} - {}", vueProjectScenario.getValue(), vueProjectScenario.getText());

                VueProjectAiService vueProjectAiService = vueProjectAiServiceFactory.getVueProjectAiServiceWithSmartTools(appId, vueProjectScenario, userMessage);

                TokenStream tokenStream = switch (vueProjectScenario) {
                    case VUE_PROJECT_CREATE -> vueProjectAiService.createVueProjectCodeStream(appId, userMessage);
                    case VUE_PROJECT_EDIT -> vueProjectAiService.editVueProjectCodeStream(appId, userMessage);
                    default -> throw new IllegalStateException("Unexpected value: " + vueProjectScenario);
                };

                yield processTokenStream(tokenStream, appId, vueProjectScenario);
            }
            case BACKEND_PROJECT_CREATE, BACKEND_PROJECT_EDIT -> {
                CodeGenTypeEnum backendScenario = codeGenTypeEnum;
                log.info("Java后端项目场景执行: {} - {}", backendScenario.getValue(), backendScenario.getText());

                BackendProjectAiService backendAiService = backendProjectAiServiceFactory
                        .getBackendProjectAiServiceWithSmartTools(appId, backendScenario, userMessage);

                TokenStream tokenStream = switch (backendScenario) {
                    case BACKEND_PROJECT_CREATE -> backendAiService.createBackendProjectCodeStream(appId, userMessage);
                    case BACKEND_PROJECT_EDIT -> backendAiService.editBackendProjectCodeStream(appId, userMessage);
                    default -> throw new IllegalStateException("Unexpected value: " + backendScenario);
                };

                yield processTokenStream(tokenStream, appId, backendScenario);
            }
            // ================= 新增：全栈前端生成分支 =================
            case FRONTEND_FULLSTACK -> {
                log.info("前端全栈项目场景执行: {}", codeGenTypeEnum.getText());

                // 获取 Vue 项目 AI 服务（传入 VUE_PROJECT_CREATE 以复用它的 writeFile 写入工具）
                VueProjectAiService vueProjectAiService = vueProjectAiServiceFactory
                        .getVueProjectAiServiceWithSmartTools(appId, CodeGenTypeEnum.VUE_PROJECT_CREATE, userMessage);

                // 调用专为全栈前端准备的流式生成接口
                TokenStream tokenStream = vueProjectAiService.createFullStackFrontendStream(appId, userMessage);

                yield processTokenStream(tokenStream, appId, codeGenTypeEnum);
            }
            // =======================================================
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 处理Token流 (修复版)
     *
     * @param tokenStream 令牌流
     * @param appId       应用ID
     * @param codeGenType 场景类型 (用于判断是否需要传统方式构建)
     * @return 处理后的字符串流
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId, CodeGenTypeEnum codeGenType) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse completeResponse) -> {
                        // 【核心修复】：仅在传统的 Vue 纯前端创建模式下，才在这里同步构建。
                        // 全栈模式 (FRONTEND_VUE_FULLSTACK) 的构建将交由后续的 ProjectBuilderNode 在工作流中统一处理！
                        if (codeGenType == CodeGenTypeEnum.VUE_PROJECT_CREATE) {
                            log.info("触发传统模式下的 Vue 项目同步构建...");
                            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_create_" + appId;
                            vueProjectBuilder.buildProject(projectPath);
                        }
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        log.error("大模型流式输出发生异常", error);
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