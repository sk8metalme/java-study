# RabbitMQリアルタイム通知実装ハンズオン

## 1. はじめに

このドキュメントでは、RabbitMQを使ったリアルタイム通知機能を実装します。

### 1.1 リアルタイム通知とは？

メッセージが送信されたら、チャンネルメンバー全員に**即座に**通知する仕組みです。

**従来の方法（ポーリング）**:
```text
クライアント: "新しいメッセージある？" (毎5秒)
サーバー: "ないよ"
クライアント: "新しいメッセージある？"
サーバー: "ないよ"
...
```

**イベント駆動（RabbitMQ）**:
```text
サーバー: メッセージ送信 → RabbitMQにイベント発行
RabbitMQ: イベントを購読者に配信
クライアント: 即座に通知を受け取る
```

### 1.2 実装する機能

- メッセージ送信イベントのPublish
- イベントのSubscribe（ログ出力）
- 将来的にWebSocket/SSEで通知（発展編）

---

## 2. RabbitMQ の基礎知識

### 2.1 主要概念

| 用語 | 説明 |
|-----|------|
| **Producer** | イベントを発行する側 |
| **Consumer** | イベントを受け取る側 |
| **Queue** | イベントを保持するキュー |
| **Exchange** | メッセージのルーティング |
| **Binding** | ExchangeとQueueの紐付け |

### 2.2 Exchange の種類

- **Direct**: ルーティングキーで完全一致
- **Topic**: パターンマッチング（`*.message.#`等）
- **Fanout**: 全てのキューにブロードキャスト
- **Headers**: ヘッダー情報でルーティング

MiniSlackでは**Topic Exchange**を使用します。

---

## 3. Step 1: RabbitMQ設定

### 3.1 RabbitMQConfig

**ファイル**: `src/main/java/com/minislack/infrastructure/messaging/rabbitmq/RabbitMQConfig.java`

```java
package com.minislack.infrastructure.messaging.rabbitmq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

/**
 * RabbitMQ設定
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "minislack.exchange";
    public static final String QUEUE_NAME = "minislack.messages.queue";
    public static final String ROUTING_KEY = "message.sent";

    @Bean
    @NonNull
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    @NonNull
    public Queue queue() {
        return new Queue(QUEUE_NAME, true); // durable = true
    }

    @Bean
    @NonNull
    public Binding binding(@NonNull Queue queue, @NonNull TopicExchange exchange) {
        return BindingBuilder.bind(queue)
                            .to(exchange)
                            .with(ROUTING_KEY);
    }

    @Bean
    @NonNull
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    @NonNull
    public RabbitTemplate rabbitTemplate(@NonNull ConnectionFactory connectionFactory,
                                         @NonNull MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
```

**学習ポイント**:
- ✅ **TopicExchange**: パターンマッチングでルーティング
- ✅ **Queue**: イベントを保持
- ✅ **Binding**: ExchangeとQueueを紐付け
- ✅ **Jackson2JsonMessageConverter**: オブジェクトをJSON化

**⚠️ 本番環境への推奨事項**:

現在の設定は学習用です。本番環境では以下を検討してください：

1. **Dead Letter Queue (DLQ)の設定**: 失敗したメッセージを失わないために必須
   - 詳細は「10. エラーハンドリング」セクション参照
   - 初期設定時点でDLQを組み込むことを推奨

2. **永続化設定**: `durable = true`でキュー永続化（再起動後も保持）

3. **リトライポリシー**: `application.yml`でリトライ設定

**推奨**: 本番環境では、このセクションで作成する Queue の設定時に DLQ を同時に設定することを強く推奨します。

---

## 4. Step 2: イベントオブジェクトの作成

### 4.1 MessageSentEvent

**ファイル**: `src/main/java/com/minislack/domain/event/MessageSentEvent.java`

```java
package com.minislack.domain.event;

import java.time.LocalDateTime;

import org.springframework.lang.NonNull;

/**
 * メッセージ送信イベント
 */
public class MessageSentEvent {
    private final String messageId;
    private final String channelId;
    private final String userId;
    private final String content;
    private final LocalDateTime occurredAt;

    public MessageSentEvent(@NonNull String messageId, @NonNull String channelId, 
                           @NonNull String userId, @NonNull String content) {
        this.messageId = messageId;
        this.channelId = channelId;
        this.userId = userId;
        this.content = content;
        this.occurredAt = LocalDateTime.now();
    }

    @NonNull
    public String getMessageId() { return messageId; }
    
    @NonNull
    public String getChannelId() { return channelId; }
    
    @NonNull
    public String getUserId() { return userId; }
    
    @NonNull
    public String getContent() { return content; }
    
    @NonNull
    public LocalDateTime getOccurredAt() { return occurredAt; }

    @Override
    @NonNull
    public String toString() {
        return "MessageSentEvent{" +
                "messageId='" + messageId + '\'' +
                ", channelId='" + channelId + '\'' +
                ", userId='" + userId + '\'' +
                ", occurredAt=" + occurredAt +
                '}';
    }
}
```

**学習ポイント**:
- ✅ **イミュータブル**: `final`フィールド
- ✅ **タイムスタンプ**: イベント発生時刻を記録

**シリアライゼーションに関する注意**:

このイベントクラスはRabbitMQを通じてJSON形式で送信されます。Jackson（`Jackson2JsonMessageConverter`）が自動的にシリアライズ/デシリアライズします。

- 現在の実装では引数付きコンストラクタのみですが、Jacksonはリフレクションでデシリアライズ可能です
- より厳密にする場合は、以下のアノテーションを追加できます：

```java
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageSentEvent {
    private final String messageId;
    // ... 他のフィールド

    @JsonCreator
    public MessageSentEvent(
            @JsonProperty("messageId") @NonNull String messageId,
            @JsonProperty("channelId") @NonNull String channelId, 
            @JsonProperty("userId") @NonNull String userId,
            @JsonProperty("content") @NonNull String content) {
        // ...
    }
}
```

ただし、学習用途では現在のシンプルな実装で十分です。

---

## 5. Step 3: イベントPublisher

### 5.1 MessageEventPublisher

**ファイル**: `src/main/java/com/minislack/infrastructure/messaging/rabbitmq/MessageEventPublisher.java`

```java
package com.minislack.infrastructure.messaging.rabbitmq;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.minislack.domain.event.MessageSentEvent;

/**
 * メッセージイベントPublisher
 */
@Component
public class MessageEventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageEventPublisher.class);
    
    private final RabbitTemplate rabbitTemplate;

    public MessageEventPublisher(@NonNull RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate);
    }

    /**
     * メッセージ送信イベントを発行
     */
    public void publishMessageSent(@NonNull MessageSentEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                event
            );
            logger.info("Published MessageSentEvent: {}", event);
        } catch (Exception e) {
            logger.error("Failed to publish MessageSentEvent", e);
            // イベント発行失敗でもメッセージ送信は成功扱い
        }
    }
}
```

**学習ポイント**:
- ✅ **非同期**: イベント発行は非同期（メイン処理をブロックしない）
- ✅ **エラーハンドリング**: 発行失敗してもメッセージ送信は成功
- ✅ **ログ出力**: デバッグ用

---

## 6. Step 4: イベントConsumer

### 6.1 MessageEventConsumer

**ファイル**: `src/main/java/com/minislack/infrastructure/messaging/rabbitmq/MessageEventConsumer.java`

```java
package com.minislack.infrastructure.messaging.rabbitmq;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.minislack.domain.event.MessageSentEvent;

/**
 * メッセージイベントConsumer
 */
@Component
public class MessageEventConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageEventConsumer.class);

    /**
     * メッセージ送信イベントを受信
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void handleMessageSent(@NonNull MessageSentEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        
        logger.info("Received MessageSentEvent: {}", event);
        logger.info("New message in channel {}: {}", event.getChannelId(), event.getContent());
        
        // TODO: ここでWebSocketやSSEを使ってクライアントに通知
        // TODO: またはメール通知、プッシュ通知等
    }
}
```

**学習ポイント**:
- ✅ **@RabbitListener**: 自動的にキューから受信
- ✅ **非同期処理**: 別スレッドで実行
- ✅ **拡張性**: 将来的にWebSocket等を追加可能

---

## 7. Step 5: MessageServiceの更新

### 7.1 イベント発行の追加

**ファイル**: `src/main/java/com/minislack/application/message/MessageService.java`を更新

```java
@Service
public class MessageService {
    private final IMessageRepository messageRepository;
    private final IChannelMembershipRepository membershipRepository;
    private final MessageEventPublisher eventPublisher; // 追加

    public MessageService(@NonNull IMessageRepository messageRepository,
                         @NonNull IChannelMembershipRepository membershipRepository,
                         @NonNull MessageEventPublisher eventPublisher) { // 追加
        this.messageRepository = Objects.requireNonNull(messageRepository);
        this.membershipRepository = Objects.requireNonNull(membershipRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher); // 追加
    }

    @Transactional
    @NonNull
    public MessageId sendMessage(@NonNull SendMessageCommand command, @NonNull UserId senderId) {
        Objects.requireNonNull(command);
        Objects.requireNonNull(senderId);
        
        ChannelId channelId = ChannelId.of(command.getChannelId());
        MessageContent content = new MessageContent(command.getContent());

        if (!membershipRepository.existsByChannelAndUser(channelId, senderId)) {
            throw new AuthorizationException("User is not a member of this channel");
        }

        Message message = new Message(MessageId.newId(), channelId, senderId, content);
        Message savedMessage = messageRepository.save(message);

        // イベント発行（追加）
        MessageSentEvent event = new MessageSentEvent(
            savedMessage.getMessageId().getValue(),
            savedMessage.getChannelId().getValue(),
            savedMessage.getUserId().getValue(),
            savedMessage.getContent().getValue()
        );
        eventPublisher.publishMessageSent(event);

        return savedMessage.getMessageId();
    }

    // ... 他のメソッドは変更なし
}
```

**学習ポイント**:
- ✅ **イベント駆動**: メッセージ保存後にイベント発行
- ✅ **疎結合**: Publisherに依存（RabbitMQの詳細は知らない）

**⚠️ パフォーマンス最適化（発展）**:

現在の実装では、イベント発行は同期実行です。これは以下の懸念があります：

1. **RabbitMQ障害時**: イベント発行失敗がメッセージ送信APIのレスポンスに影響
2. **レスポンスタイム**: イベント発行待機がAPIレスポンスを遅延

**本番環境での推奨パターン（非同期イベント発行）**:

```java
@Service
public class MessageService {
    private final MessageEventPublisher eventPublisher;
    private final Executor taskExecutor;

    @Transactional
    @NonNull
    public MessageId sendMessage(...) {
        // 1. メッセージ保存（同期）
        Message savedMessage = messageRepository.save(message);

        // 2. イベント発行（非同期・トランザクション外）
        CompletableFuture.runAsync(() -> {
            MessageSentEvent event = new MessageSentEvent(...);
            eventPublisher.publishMessageSent(event);
        }, taskExecutor);

        return savedMessage.getMessageId();
    }
}
```

ただし、学習用途では現在の同期実装でイベント駆動の基本を理解することが重要です。非同期化は応用編として検討してください。

---

## 8. Step 6: 動作確認

### 8.1 アプリケーション起動

```bash
# RabbitMQが起動しているか確認
docker-compose ps

# アプリケーション起動
./gradlew bootRun
```

起動ログで以下を確認：
```
Created Exchange: minislack.exchange
Created Queue: minislack.messages.queue
Created Binding: message.sent
```

### 8.2 メッセージ送信テスト

```bash
curl -X POST http://localhost:8080/api/v1/channels/$CHANNEL_ID/messages \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $USER_ID" \
  -d '{
    "content": "Testing RabbitMQ integration!"
  }' | jq
```

**アプリケーションログに以下が出力されるはず**:
```
Published MessageSentEvent: MessageSentEvent{messageId='...', channelId='...', ...}
Received MessageSentEvent: MessageSentEvent{messageId='...', channelId='...', ...}
New message in channel 650e8400...: Testing RabbitMQ integration!
```

### 8.3 RabbitMQ管理画面で確認

ブラウザで`http://localhost:15672`にアクセス：

- ユーザー名: `minislack`
- パスワード: `password`

**確認項目**:
1. **Exchanges**: `minislack.exchange`が存在
2. **Queues**: `minislack.messages.queue`が存在
3. **Bindings**: `minislack.exchange` → `minislack.messages.queue`

### 8.4 メッセージフローの確認

```text
1. POST /api/v1/channels/{id}/messages
   ↓
2. MessageService.sendMessage()
   ↓
3. messageRepository.save()  // DB保存
   ↓
4. eventPublisher.publishMessageSent()  // RabbitMQに発行
   ↓
5. RabbitMQ Exchange → Queue
   ↓
6. MessageEventConsumer.handleMessageSent()  // 受信
   ↓
7. ログ出力「New message in channel ...」
```

---

## 9. 実践：通知機能の拡張

### 9.1 チャンネル更新イベント

**ファイル**: `src/main/java/com/minislack/domain/event/ChannelMemberJoinedEvent.java`

```java
package com.minislack.domain.event;

import java.time.LocalDateTime;

import org.springframework.lang.NonNull;

/**
 * チャンネルメンバー参加イベント
 */
public class ChannelMemberJoinedEvent {
    private final String channelId;
    private final String userId;
    private final LocalDateTime occurredAt;

    public ChannelMemberJoinedEvent(@NonNull String channelId, @NonNull String userId) {
        this.channelId = channelId;
        this.userId = userId;
        this.occurredAt = LocalDateTime.now();
    }

    @NonNull
    public String getChannelId() { return channelId; }
    
    @NonNull
    public String getUserId() { return userId; }
    
    @NonNull
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
```

### 9.2 イベントPublisher更新

**ファイル**: `MessageEventPublisher.java`に追加

```java
public void publishMemberJoined(@NonNull ChannelMemberJoinedEvent event) {
    Objects.requireNonNull(event);
    
    try {
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE_NAME,
            "channel.member.joined",
            event
        );
        logger.info("Published ChannelMemberJoinedEvent: {}", event);
    } catch (Exception e) {
        logger.error("Failed to publish ChannelMemberJoinedEvent", e);
    }
}
```

### 9.3 Consumer更新

```java
@RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
public void handleMemberJoined(@NonNull ChannelMemberJoinedEvent event) {
    logger.info("User {} joined channel {}", event.getUserId(), event.getChannelId());
    // TODO: WebSocket通知等
}
```

### 9.4 ChannelManagementServiceの更新

```java
@Service
public class ChannelManagementService {
    private final MessageEventPublisher eventPublisher; // 追加

    @Transactional
    public void joinChannel(@NonNull UserId userId, @NonNull ChannelId channelId) {
        membershipService.joinChannel(userId, channelId);
        
        // イベント発行
        ChannelMemberJoinedEvent event = new ChannelMemberJoinedEvent(
            channelId.getValue(),
            userId.getValue()
        );
        eventPublisher.publishMemberJoined(event);
    }
}
```

---

## 10. エラーハンドリング

### 10.1 リトライ設定

`application.yml`に追加：

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        retry:
          enabled: true
          initial-interval: 1000
          max-attempts: 3
          multiplier: 2
```

### 10.2 Dead Letter Queue（DLQ）

**ファイル**: `RabbitMQConfig.java`に追加

```java
public static final String DLQ_NAME = "minislack.messages.dlq";

@Bean
@NonNull
public Queue deadLetterQueue() {
    return new Queue(DLQ_NAME, true);
}

@Bean
@NonNull
public Queue queue() {
    Map<String, Object> args = new HashMap<>();
    args.put("x-dead-letter-exchange", "");
    args.put("x-dead-letter-routing-key", DLQ_NAME);
    return new Queue(QUEUE_NAME, true, false, false, args);
}
```

**学習ポイント**:
- ✅ **リトライ**: 失敗時に自動再試行
- ✅ **DLQ**: 最終的に失敗したメッセージを保存

---

## 11. パフォーマンステスト

### 11.1 大量メッセージのイベント発行

```bash
# 100件のメッセージを連続送信
for i in {1..100}; do
  curl -s -X POST http://localhost:8080/api/v1/channels/$CHANNEL_ID/messages \
    -H "Content-Type: application/json" \
    -H "X-User-Id: $USER_ID" \
    -d "{\"content\": \"Bulk message $i\"}" > /dev/null
  echo "Sent message $i"
done
```

**確認**:
- アプリケーションログに100件のPublish/Receiveログが出力される
- RabbitMQ管理画面で処理速度を確認

### 11.2 RabbitMQ管理画面でメトリクス確認

1. `http://localhost:15672`にアクセス
2. Queues タブ → `minislack.messages.queue`
3. **Message rates** グラフを確認
   - Publish rate: メッセージ発行速度
   - Deliver rate: メッセージ配信速度

---

## 12. まとめ

### 12.1 実装したもの

- ✅ RabbitMQ設定（Exchange, Queue, Binding）
- ✅ イベントオブジェクト（MessageSentEvent）
- ✅ イベントPublisher
- ✅ イベントConsumer
- ✅ MessageServiceへのイベント発行統合

### 12.2 動作確認した機能

- ✅ メッセージ送信時のイベント発行
- ✅ イベントの受信とログ出力
- ✅ RabbitMQ管理画面での確認
- ✅ 大量メッセージの処理

### 12.3 学んだこと

- ✅ **イベント駆動アーキテクチャ**: 疎結合な設計
- ✅ **Publisher/Subscriber**: 非同期通信パターン
- ✅ **RabbitMQの基礎**: Exchange, Queue, Binding
- ✅ **エラーハンドリング**: リトライ、DLQ

### 12.4 次のステップ

RabbitMQリアルタイム通知が完成しました！次はバッチ処理を実装します：

- [14-batch-processing.md](14-batch-processing.md) - バッチ処理実装

---

## 13. 発展：WebSocket統合（将来実装）

現在はConsumerでログ出力のみですが、実際のアプリケーションでは：

```java
@Component
public class MessageEventConsumer {
    private final SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void handleMessageSent(MessageSentEvent event) {
        // WebSocketで全クライアントに通知
        messagingTemplate.convertAndSend(
            "/topic/channel/" + event.getChannelId(),
            event
        );
    }
}
```

これにより、リアルタイムチャットが実現できます！

---

## 14. よくある質問

### Q1. なぜRabbitMQを使うのか？

**A**: 
- 非同期処理（パフォーマンス向上）
- システム間の疎結合
- スケーラビリティ（複数のConsumerで負荷分散）

### Q2. イベント発行が失敗したらメッセージも失敗する？

**A**: いいえ。現在の実装では、メッセージはDBに保存済みなので、イベント発行失敗してもメッセージ送信は成功扱いです。

### Q3. RabbitMQが停止したらどうなる？

**A**: イベント発行時に例外が発生しますが、catchしているので、メッセージ送信自体は成功します。ただし、リアルタイム通知は届きません。

---

## 15. 参考資料

- [Spring AMQP](https://spring.io/projects/spring-amqp)
- [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials)
- [Event-Driven Architecture](https://martinfowler.com/articles/201701-event-driven.html)

