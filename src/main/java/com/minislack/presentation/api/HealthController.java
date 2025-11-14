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

/**
 * ヘルスチェック・動作確認用コントローラー
 * 
 * 注意: このコントローラーは動作確認用です。
 * 本番実装では、Presentation層がInfrastructure層に直接依存すべきではありません。
 * Application層を経由してビジネスロジックを実行してください。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final TestRepository testRepository;
    private final TestPublisher testPublisher;

    public HealthController(TestRepository testRepository, TestPublisher testPublisher) {
        this.testRepository = testRepository;
        this.testPublisher = testPublisher;
    }

    /**
     * ヘルスチェックエンドポイント
     * アプリケーションとデータベースの接続状態を確認
     * 
     * @return ステータス情報（status, timestamp, message, dbRecordCount）
     */
    @GetMapping("/health")
    public Map<String,Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("timestamp", LocalDateTime.now());
        response.put("message", "MiniSlack is running!");
        response.put("dbRecordCount", testRepository.count());
        return response;
    }

    /**
     * テストデータ作成エンドポイント
     * PostgreSQL接続確認用
     * 
     * @param body リクエストボディ（name: テストデータの名前）
     * @return 作成されたTestEntity
     */
    @PostMapping("/test")
    public TestEntity createTest(@RequestBody Map<String, String> body) {
        TestEntity entity = new TestEntity();
        entity.setName(body.get("name"));
        return testRepository.save(entity);
    }

    /**
     * RabbitMQテストエンドポイント
     * メッセージキュー接続確認用
     *
     * @param body リクエストボディ（message: 送信するメッセージ）
     * @return ステータス情報（status, message, timestamp）
     */
    @PostMapping("/rabbitmq-test")
    public Map<String, Object> testRabbitMQ(@RequestBody Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            String message = body.get("message");
            if (message == null || message.trim().isEmpty()) {
                response.put("status", "ERROR");
                response.put("message", "メッセージが空です");
                response.put("timestamp", LocalDateTime.now());
                return response;
            }

            testPublisher.sendMessage(message);
            response.put("status", "SUCCESS");
            response.put("message", "Message sent to RabbitMQ successfully");
            response.put("timestamp", LocalDateTime.now());
            return response;
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("message", "RabbitMQへのメッセージ送信に失敗しました: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            return response;
        }
    }
}
