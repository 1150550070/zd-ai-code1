package com.sht.zdaicode.ai;

import com.sht.zdaicode.ai.model.scheam.ProjectScheam;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface DatabaseDesignAiService {
    @SystemMessage(fromResource = "prompt/database-design-system-prompt.txt")
    ProjectScheam designDatabase(@UserMessage String userMessage);
}
