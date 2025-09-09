package com.sht.zdaicode.ai;

import com.sht.zdaicode.ai.model.HtmlCodeResult;
import com.sht.zdaicode.core.AiCodeGeneratorFacade;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiCodeGeneratorServiceTest {

    @Autowired
    private AiCodeGeneratorService aiCodeGeneratorService;
    @Autowired
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generateHtmlCode() {
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode("生成一个简单的HTML页面,不超过20行");
        Assertions.assertNotNull(result);
    }

    @Test
    void testChatMemory() {
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode( "做个程序员鱼皮的工具网站，总代码量不超过 20 行");
        Assertions.assertNotNull(result);
        result = aiCodeGeneratorService.generateHtmlCode( "不要生成网站，告诉我你刚刚做了什么？");
        Assertions.assertNotNull(result);
        result = aiCodeGeneratorService.generateHtmlCode( "做个程序员鱼皮的工具网站，总代码量不超过 20 行");
        Assertions.assertNotNull(result);
        result = aiCodeGeneratorService.generateHtmlCode( "不要生成网站，告诉我你刚刚做了什么？");
        Assertions.assertNotNull(result);
    }



}