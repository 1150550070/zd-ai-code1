package com.sht.zdaicode.ai;

import com.sht.zdaicode.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.SystemMessage;

/**
 * AI代码生成类型智能路由服务
 * 使用结构化输出直接返回枚举类型
 *
 * 
 */
public interface AiCodeGenTypeRoutingService {

    /**
     * 根据用户需求智能选择代码生成类型
     *
     * @param userPrompt 用户输入的需求描述
     * @return 推荐的代码生成类型
     */
    @SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
    CodeGenTypeEnum routeCodeGenType(String userPrompt);

    /**
     * 根据用户需求智能选择Vue项目场景(创建模式/编辑模式)
     *
     * @param userPrompt 用户输入的需求描述
     * @return 推荐的Vue项目场景(创建模式/编辑模式)
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-scenario-routing-system-prompt.txt")
    CodeGenTypeEnum routeVueProjectScenario(String userPrompt);

    /**
     * 根据全栈环境的需求智能选择前端代码生成类型
     *
     * @param userPrompt 全栈需求与 Schema 描述
     * @return 推荐的全栈前端生成类型
     */
    @SystemMessage(fromResource = "prompt/codegen-fullstack-routing-system-prompt.txt")
    CodeGenTypeEnum routeFullStackCodeGenType(String userPrompt);
}

