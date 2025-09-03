package com.sht.zdaicode;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.sht.zdaicode.mapper")
public class ZdAiCodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZdAiCodeApplication.class, args);
    }

}
