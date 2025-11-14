package com.minislack.infrastructure.messaging.rabbitmq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * テスト用RabbitMQパブリッシャー
 * test-queueにメッセージを送信
 */
@Component
public class TestPublisher {

    private static final Logger log = LoggerFactory.getLogger(TestPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public TestPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * メッセージを送信
     *
     * @param message 送信するメッセージ
     * @throws RuntimeException RabbitMQ接続エラー時やメッセージ送信失敗時
     */
    public void sendMessage(String message) {
        try {
            log.debug("Sending message to RabbitMQ: {}", message);
            rabbitTemplate.convertAndSend(RabbitMQConfig.TEST_QUEUE, message);
            log.info("Successfully sent message to RabbitMQ queue: {}", RabbitMQConfig.TEST_QUEUE);
        } catch (AmqpException e) {
            log.error("Failed to send message to RabbitMQ queue: {}. Message: {}",
                     RabbitMQConfig.TEST_QUEUE, message, e);
            throw new RuntimeException("RabbitMQへのメッセージ送信に失敗しました", e);
        } catch (Exception e) {
            log.error("Unexpected error while sending message to RabbitMQ. Message: {}", message, e);
            throw new RuntimeException("メッセージ送信中に予期しないエラーが発生しました", e);
        }
    }
}
