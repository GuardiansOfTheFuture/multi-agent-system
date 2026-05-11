package com.paperai.controller.demo;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * @author: ch
 * @date 2026年05月11日 11:19
 */
@RestController
@RequestMapping("/demo")
public class TestController {

    @Resource
    private ChatClient dashScopeChatClient;

    @GetMapping("/testDashScope")
    public String testDashScope() {
        return dashScopeChatClient.prompt("你好，请用一句话介绍你自己")
                .call()
                .content();
    }

    @GetMapping(value = "/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatSse(@RequestParam String prompt) {
        return dashScopeChatClient.prompt(prompt)
                .stream()
                .content();
    }

}
