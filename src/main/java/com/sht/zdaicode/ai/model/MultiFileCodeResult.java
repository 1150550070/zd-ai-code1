package com.sht.zdaicode.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 多文件代码结果
 */
@Description("生成多文件代码文件的结果")
@Data
public class MultiFileCodeResult {
    @Description("HTML代码")
    private String htmlCode;
    @Description("CSS代码")
    private String cssCode;
    @Description("JS代码")
    private String jsCode;
    @Description("多文件代码描述")
    private String description;
}
