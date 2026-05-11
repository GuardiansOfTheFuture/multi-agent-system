package com.paperai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 应用启动测试 — 验证 Spring 上下文能正常加载
 */
@SpringBootTest
class PaperAiApplicationTest {

    @Test
    void contextLoads() {
        // 能启动就代表所有 Bean 注入成功
    }
}
