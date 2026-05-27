package com.paperai.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqConfig {

    public static final String EXCHANGE = "paperai.exchange";
    public static final String DLX = "paperai.dlx";
    public static final String DLQ = "paperai.q.dlq";

    // ===== Message Converter =====

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ===== Exchanges =====

    @Bean
    public TopicExchange paperaiExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange dlxExchange() {
        return ExchangeBuilder.directExchange(DLX).durable(true).build();
    }

    // ===== Dead Letter Queue =====

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue()).to(dlxExchange()).with(DLQ);
    }

    // ===== Paper Write Queue =====

    @Bean
    public Queue paperWriteQueue() {
        return QueueBuilder.durable("paperai.q.paper.write")
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .withArgument("x-message-ttl", 1800000)
                .withArgument("x-max-priority", 10)
                .build();
    }

    @Bean
    public Binding paperWriteBinding() {
        return BindingBuilder.bind(paperWriteQueue()).to(paperaiExchange()).with("paperai.paper.write");
    }

    // ===== Knowledge Upload Queue =====

    @Bean
    public Queue knowledgeUploadQueue() {
        return QueueBuilder.durable("paperai.q.knowledge.upload")
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .withArgument("x-message-ttl", 600000)
                .withArgument("x-max-priority", 10)
                .build();
    }

    @Bean
    public Binding knowledgeUploadBinding() {
        return BindingBuilder.bind(knowledgeUploadQueue()).to(paperaiExchange()).with("paperai.knowledge.upload");
    }

    // ===== Knowledge Graph Extract Queue =====

    @Bean
    public Queue kgExtractQueue() {
        return QueueBuilder.durable("paperai.q.kg.extract")
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .withArgument("x-message-ttl", 600000)
                .build();
    }

    @Bean
    public Binding kgExtractBinding() {
        return BindingBuilder.bind(kgExtractQueue()).to(paperaiExchange()).with("paperai.kg.extract");
    }

    // ===== Paper Export Queue =====

    @Bean
    public Queue paperExportQueue() {
        return QueueBuilder.durable("paperai.q.paper.export")
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .withArgument("x-message-ttl", 300000)
                .build();
    }

    @Bean
    public Binding paperExportBinding() {
        return BindingBuilder.bind(paperExportQueue()).to(paperaiExchange()).with("paperai.paper.export");
    }

    // ===== Agent Edit Queue =====

    @Bean
    public Queue agentEditQueue() {
        return QueueBuilder.durable("paperai.q.paper.agent-edit")
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .withArgument("x-message-ttl", 300000)
                .build();
    }

    @Bean
    public Binding agentEditBinding() {
        return BindingBuilder.bind(agentEditQueue()).to(paperaiExchange()).with("paperai.paper.agent-edit");
    }

    // ===== Research Queue =====

    @Bean
    public Queue researchQueue() {
        return QueueBuilder.durable("paperai.q.paper.research")
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .withArgument("x-message-ttl", 600000)
                .build();
    }

    @Bean
    public Binding researchBinding() {
        return BindingBuilder.bind(researchQueue()).to(paperaiExchange()).with("paperai.paper.research");
    }
}
