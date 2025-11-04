package com.minislack.presentation.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minislack.infrastructure.messaging.rabbitmq.TestPublisher;
import com.minislack.infrastructure.persistence.TestEntity;
import com.minislack.infrastructure.persistence.TestRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final TestRepository testRepository;
    private final TestPublisher testPublisher;

    public HealthController(TestRepository testRepository, TestPublisher testPublisher) {
        this.testRepository = testRepository;
        this.testPublisher = testPublisher;
    }

    @GetMapping("/health")
    public Map<String,Object> health() {

        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("timestamp", LocalDateTime.now());
        response.put("message", "MiniSlack is running!");
        response.put("dbRecordCount", testRepository.count());
        return response;
    }

    @PostMapping("/test")
    public TestEntity createTest(@RequestBody Map<String, String> body) {
        TestEntity entity = new TestEntity();
        entity.setName(body.get("name"));
        return testRepository.save(entity);
    }

    @PostMapping("/rabbitmq-test")
    public Map<String, String> testRabbitMQ(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        testPublisher.sendMessage(message);
        return Map.of("status", "Message sent to RabbitMQ");
    }
}
