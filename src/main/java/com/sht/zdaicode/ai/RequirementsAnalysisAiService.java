package com.sht.zdaicode.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface RequirementsAnalysisAiService {
    @SystemMessage(fromResource = "prompt/requirements-analysis-system-prompt.txt")
    String analyzeRequirements(@UserMessage String userMessage);
}
