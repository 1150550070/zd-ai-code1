package com.sht.zdaicode.ai;

import com.sht.zdaicode.ai.model.HtmlCodeResult;
import com.sht.zdaicode.ai.model.MultiFileCodeResult;
import com.sht.zdaicode.core.AiCodeGeneratorFacade;
import com.sht.zdaicode.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.File;

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
    void generateMultiFileCode() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream("我是大学生我叫王旭莹,你可以叫我莹莹大王,生成一个简单的个人主页,不超过300行", CodeGenTypeEnum.MULTI_FILE);
        Assertions.assertNotNull(codeStream);
    }
}