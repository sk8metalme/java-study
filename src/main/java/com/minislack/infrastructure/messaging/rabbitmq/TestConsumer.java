package com.minislack.infrastructure.messaging.rabbitmq;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TestConsumer {

    @RabbitListener(queues = RabbitMQConfig.TEST_QUEUE)
    public void receiveMessage(String message) {
        System.out.println("Received from RabbitMQ: " + message);
    }
}
