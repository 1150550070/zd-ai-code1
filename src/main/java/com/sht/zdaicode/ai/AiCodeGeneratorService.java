package com.sht.zdaicode.ai;

import com.sht.zdaicode.ai.model.HtmlCodeResult;
import com.sht.zdaicode.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;

/**
 * AI 代码生成服务
 */
public interface AiCodeGeneratorService {

//    /**
//     * 生成HTML代码
//     *
//     * @param userMessage 用户输入
//     * @return 生成的代码
//     */
//    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
//    HtmlCodeResult generateHtmlCode(String userMessage);
//
//    /**
//     * 生成多文件代码
//     *
//     * @param userMessage 用户输入
//     * @return 生成的代码
//     */
//    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
//    MultiFileCodeResult generateMultiFileCode(String userMessage);



    /**
     * 生成HTML代码
     *
     * @param userMessage 用户输入
     * @return 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(String userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage 用户输入
     * @return 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);

    /**
     * 生成全栈 HTML 代码（带真实API请求）
     */
    @SystemMessage(fromResource = "prompt/codegen-frontend-html-fullstack-system-prompt.txt")
    Flux<String> generateFullStackHtmlCodeStream(String userMessage);

    /**
     * 生成全栈 多文件代码（带真实API请求）
     */
    @SystemMessage(fromResource = "prompt/codegen-frontend-multi-file-fullstack-system-prompt.txt")
    Flux<String> generateFullStackMultiFileCodeStream(String userMessage);

    /**
     * 生成 Java 后端代码
     */
    @SystemMessage(fromResource = "prompt/codegen-backend-java-create-system-prompt.txt")
    TokenStream generateBackendJavaCodeStream(String userMessage);


    // Vue 项目生成方法已迁移到 VueProjectAiService

}
