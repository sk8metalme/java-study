package com.minislack.infrastructure.messaging.rabbitmq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * テスト用RabbitMQパブリッシャー
 * test-queueにメッセージを送信
 */
@Component
public class TestPublisher {

    private final RabbitTemplate rabbitTemplate;

    public TestPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * メッセージを送信
     * 
     * @param message 送信するメッセージ
     * @throws org.springframework.amqp.AmqpException RabbitMQ接続エラー時
     *         （本番環境では適切なエラーハンドリングを実装してください）
     */
    public void sendMessage(String message) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.TEST_QUEUE, message);
    }
}
