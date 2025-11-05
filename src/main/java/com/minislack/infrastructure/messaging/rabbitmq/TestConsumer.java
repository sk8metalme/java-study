package com.minislack.infrastructure.messaging.rabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * テスト用RabbitMQコンシューマー
 * test-queueからメッセージを受信
 */
@Component
public class TestConsumer {

    private static final Logger log = LoggerFactory.getLogger(TestConsumer.class);

    @RabbitListener(queues = RabbitMQConfig.TEST_QUEUE)
    public void receiveMessage(String message) {
        log.info("Received from RabbitMQ: {}", message);
    }
}
