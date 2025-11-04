package com.minislack.infrastructure.messaging.rabbitmq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TestPublisher {

    private final RabbitTemplate rabbitTemplate;

    public TestPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendMessage(String message) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.TEST_QUEUE, message);
    }
}
