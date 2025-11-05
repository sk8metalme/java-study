package com.minislack.infrastructure.messaging.rabbitmq;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ設定
 * テスト用キューを定義
 */
@Configuration
public class RabbitMQConfig {

    public static final String TEST_QUEUE = "test-queue";

    /**
     * テスト用キューの定義
     * @return 永続化されたキュー（durable=true）
     */
    @Bean
    public Queue testQueue() {
        return new Queue(TEST_QUEUE, true);
    }
}
