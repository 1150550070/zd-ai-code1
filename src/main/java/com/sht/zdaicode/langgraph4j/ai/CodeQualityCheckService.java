package com.sht.zdaicode.langgraph4j.ai;

import com.sht.zdaicode.langgraph4j.model.QualityResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface CodeQualityCheckService {

    /**
     * 检查代码质量
     * AI 会分析代码并返回质量检查结果
     */
    @SystemMessage(fromResource = "prompt/code-quality-check-system-prompt.txt")
    QualityResult checkCodeQuality(@UserMessage String codeContent);

    /**
     * 检查全栈前端代码质量（需要验证 API 契约和数据绑定）
     */
    @SystemMessage(fromResource = "prompt/codegen-frontend-quality-check-fullstack-system-prompt.txt")
    QualityResult checkFullstackFrontendCodeQuality(@UserMessage String codeContent);

    /**
     * 检查后端 Java 代码质量（验证 Schema 和分层结构）
     */
    @SystemMessage(fromResource = "prompt/codegen-backend-quality-check-system-prompt.txt")
    QualityResult checkBackendJavaCodeQuality(@UserMessage String codeContent);
}
