package com.sht.zdaicode.langgraph4j.ai;

import com.sht.zdaicode.langgraph4j.model.QualityResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface DbQualityCheckService {

    /**
     * 检查数据库设计质量
     * AI 会分析 SQL 语句并返回质量检查结果
     */
    @SystemMessage(fromResource = "prompt/db-quality-check-system-prompt.txt")
    QualityResult checkDbQuality(@UserMessage String sqlContent);
}
