package com.sht.zdaicode.core;

import com.sht.zdaicode.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiCodeGeneratorFacadeTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generateAndSaveCode() {
        File file = aiCodeGeneratorFacade.generateAndSaveCode("写一个html页面，包含一个h1标题，标题内容为：hello world", CodeGenTypeEnum.HTML, 1L);
        Assertions.assertNotNull(file);
    }

    @Test
    void generateAndSaveCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream("写一个html页面，包含一个h1标题，标题内容为：hello world", CodeGenTypeEnum.HTML, 1L);
        // 阻塞式获取所有代码
        List<String> result = codeStream.collectList().block();
        Assertions.assertNotNull(result);
        // 拼接字符串,得到完整内容
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }
}