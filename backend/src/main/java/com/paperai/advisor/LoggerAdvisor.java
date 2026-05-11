package com.paperai.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * 自定义日志 Advisor - 记录 AI 请求/响应/耗时
 *
 * @author: ch
 * @date 2026年05月11日 13:46
 */
public class LoggerAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    private static final Logger log = LoggerFactory.getLogger(LoggerAdvisor.class);

    private final int order;

    public LoggerAdvisor() {
        this.order = 0;
    }

    public LoggerAdvisor(int order) {
        this.order = order;
    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        long start = System.currentTimeMillis();

        // 打印请求
        log.info("┌─ [LLM 请求] {} 字", advisedRequest.userText().length());
        if (advisedRequest.systemText() != null) {
            log.info("├─ [系统提示] {} 字", advisedRequest.systemText().length());
        }

        // 执行调用链
        AdvisedResponse response = chain.nextAroundCall(advisedRequest);
        long elapsed = System.currentTimeMillis() - start;
        assert response.response() != null;
        String result = response.response().getResult().getOutput().getText();
        log.info("├─ [LLM 响应] {} 字，耗时 {}ms", result.length(), elapsed);
        log.info("└─ 响应首行: {}", result.length() > 120 ? result.substring(0, 120) + "..." : result);

        return response;
    }

    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        long start = System.currentTimeMillis();

        log.info("┌─ [AI 流请求] {}", advisedRequest.userText());
        if (advisedRequest.systemText() != null) {
            log.info("├─ [系统提示] {}", advisedRequest.systemText());
        }

        return chain.nextAroundStream(advisedRequest)
                .publishOn(Schedulers.boundedElastic())
                .doOnComplete(() -> {
                    long elapsed = System.currentTimeMillis() - start;
                    log.info("└─ [流耗时] {}ms", elapsed);
                });
    }

    @Override
    public String getName() {
        return "LoggerAdvisor";
    }

    @Override
    public int getOrder() {
        return this.order;
    }
}
