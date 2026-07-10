package com.sht.zdaicode.ai;

import com.sht.zdaicode.ai.model.ApiContract;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface ApiContractAiService {
    @SystemMessage(fromResource = "prompt/api-contract-system-prompt.txt")
    ApiContract generateApiContract(@UserMessage String userMessage);
}
