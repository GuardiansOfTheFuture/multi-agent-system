package com.paperai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.paperai.mapper")
public class PaperAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperAiApplication.class, args);
    }
}
