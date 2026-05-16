package com.paperai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@MapperScan("com.paperai.mapper")
@EnableCaching
public class PaperAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperAiApplication.class, args);
    }
}
