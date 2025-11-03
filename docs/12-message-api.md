# メッセージAPI実装ハンズオン

## 1. はじめに

このドキュメントでは、メッセージ送受信機能を実装します。

### 1.1 実装する機能

- メッセージ送信API
- メッセージ一覧取得API（ページネーション）
- メッセージ検索API

### 1.2 前提条件

- ユーザー管理機能（`10-user-management.md`）が実装済み
- チャンネル管理機能（`11-channel-management.md`）が実装済み

---

## 2. Step 1: ドメイン層の実装

### 2.1 値オブジェクト

```bash
mkdir -p src/main/java/com/minislack/domain/model/message
```

以下のファイルを作成してください（`06-domain-layer.md`を参照）：

1. `MessageId.java`
2. `MessageContent.java`

### 2.2 エンティティとリポジトリ

1. `Message.java`
2. `IMessageRepository.java`

---

## 3. Step 2: アプリケーション層の実装

### 3.1 コマンドオブジェクト

```bash
mkdir -p src/main/java/com/minislack/application/message
```

**ファイル**: `src/main/java/com/minislack/application/message/SendMessageCommand.java`

（`07-application-layer.md`を参照）

### 3.2 アプリケーションサービス

**ファイル**: `src/main/java/com/minislack/application/message/MessageService.java`

（`07-application-layer.md`を参照）

---

## 4. Step 3: インフラ層の実装

### 4.1 JPAエンティティ

```bash
mkdir -p src/main/java/com/minislack/infrastructure/persistence/message
```

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/message/MessageJpaEntity.java`

（`08-infrastructure-layer.md`を参照）

### 4.2 マッパー

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/message/MessageEntityMapper.java`

```java
package com.minislack.infrastructure.persistence.message;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.message.Message;
import com.minislack.domain.model.message.MessageContent;
import com.minislack.domain.model.message.MessageId;
import com.minislack.domain.model.user.UserId;

/**
 * メッセージエンティティマッパー
 */
@Component
public class MessageEntityMapper {

    @NonNull
    public MessageJpaEntity toJpaEntity(@NonNull Message message) {
        MessageJpaEntity entity = new MessageJpaEntity();
        entity.setMessageId(message.getMessageId().getValue());
        entity.setChannelId(message.getChannelId().getValue());
        entity.setUserId(message.getUserId().getValue());
        entity.setContent(message.getContent().getValue());
        entity.setCreatedAt(message.getCreatedAt());
        return entity;
    }

    @NonNull
    public Message toDomain(@NonNull MessageJpaEntity entity) {
        return new Message(
            MessageId.of(entity.getMessageId()),
            ChannelId.of(entity.getChannelId()),
            UserId.of(entity.getUserId()),
            new MessageContent(entity.getContent()),
            entity.getCreatedAt()
        );
    }
}
```

### 4.3 Spring Data JPA Repository

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/message/SpringDataMessageRepository.java`

（`08-infrastructure-layer.md`を参照）

### 4.4 リポジトリ実装

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/message/MessageRepositoryImpl.java`

（`08-infrastructure-layer.md`を参照）

---

## 5. Step 4: プレゼンテーション層の実装

### 5.1 DTO

```bash
mkdir -p src/main/java/com/minislack/presentation/api/message
```

**ファイル**: `src/main/java/com/minislack/presentation/api/message/SendMessageRequest.java`

**ファイル**: `src/main/java/com/minislack/presentation/api/message/MessageResponse.java`

（`09-presentation-layer.md`を参照）

### 5.2 コントローラー

**ファイル**: `src/main/java/com/minislack/presentation/api/message/MessageController.java`

（`09-presentation-layer.md`を参照）

---

## 6. Step 5: 動作確認

### 6.1 テストデータの準備

```bash
# 1. ユーザー登録
USER1=$(curl -s -X POST http://localhost:8080/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "password123",
    "displayName": "Alice"
  }')
USER1_ID=$(echo $USER1 | jq -r '.userId')

# 2. チャンネル作成
CHANNEL=$(curl -s -X POST http://localhost:8080/api/v1/channels \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $USER1_ID" \
  -d '{
    "channelName": "general",
    "description": "General discussion",
    "isPublic": true
  }')
CHANNEL_ID=$(echo $CHANNEL | jq -r '.channelId')

echo "User ID: $USER1_ID"
echo "Channel ID: $CHANNEL_ID"
```

### 6.2 メッセージ送信テスト

```bash
curl -X POST http://localhost:8080/api/v1/channels/$CHANNEL_ID/messages \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $USER1_ID" \
  -d '{
    "content": "Hello, everyone! This is my first message."
  }' | jq
```

**期待されるレスポンス**:
```json
{
  "messageId": "750e8400-e29b-41d4-a716-446655440000",
  "channelId": "650e8400-e29b-41d4-a716-446655440000",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "content": "Hello, everyone! This is my first message.",
  "createdAt": "2025-11-01T12:50:00"
}
```

### 6.3 複数メッセージの送信

```bash
# 2つ目のメッセージ
curl -X POST http://localhost:8080/api/v1/channels/$CHANNEL_ID/messages \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $USER1_ID" \
  -d '{
    "content": "This is my second message."
  }' | jq

# 3つ目のメッセージ
curl -X POST http://localhost:8080/api/v1/channels/$CHANNEL_ID/messages \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $USER1_ID" \
  -d '{
    "content": "And this is the third one!"
  }' | jq
```

### 6.4 メッセージ一覧取得

```bash
curl -X GET "http://localhost:8080/api/v1/channels/$CHANNEL_ID/messages?limit=10&offset=0" \
  -H "X-User-Id: $USER1_ID" | jq
```

**期待されるレスポンス**:
```json
[
  {
    "messageId": "...",
    "channelId": "...",
    "userId": "...",
    "content": "And this is the third one!",
    "createdAt": "2025-11-01T12:52:00"
  },
  {
    "messageId": "...",
    "channelId": "...",
    "userId": "...",
    "content": "This is my second message.",
    "createdAt": "2025-11-01T12:51:00"
  },
  {
    "messageId": "...",
    "channelId": "...",
    "userId": "...",
    "content": "Hello, everyone! This is my first message.",
    "createdAt": "2025-11-01T12:50:00"
  }
]
```

**学習ポイント**:
- ✅ **降順ソート**: 新しいメッセージが最初
- ✅ **ページネーション**: limit=10, offset=0

---

## 7. エラーケースのテスト

### 7.1 メンバーでないユーザーがメッセージ送信

```bash
# 別のユーザーを作成
USER2=$(curl -s -X POST http://localhost:8080/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "bob",
    "email": "bob@example.com",
    "password": "password123",
    "displayName": "Bob"
  }')
USER2_ID=$(echo $USER2 | jq -r '.userId')

# Bobはgeneralチャンネルのメンバーではない
curl -X POST http://localhost:8080/api/v1/channels/$CHANNEL_ID/messages \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $USER2_ID" \
  -d '{
    "content": "Can I post here?"
  }' | jq
```

**期待されるレスポンス**（403 Forbidden）:
```json
{
  "status": 403,
  "error": "Authorization Failed",
  "message": "User is not a member of this channel",
  "timestamp": "2025-11-01T12:53:00"
}
```

### 7.2 空メッセージ

```bash
curl -X POST http://localhost:8080/api/v1/channels/$CHANNEL_ID/messages \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $USER1_ID" \
  -d '{
    "content": ""
  }' | jq
```

**期待されるレスポンス**（400 Bad Request）:
```json
{
  "status": 400,
  "error": "Validation Failed",
  "fieldErrors": {
    "content": "Content must not be blank"
  },
  "timestamp": "2025-11-01T12:54:00"
}
```

### 7.3 長すぎるメッセージ

```bash
# 2000文字を超えるメッセージ
LONG_CONTENT=$(python3 -c "print('a' * 2001)")

curl -X POST http://localhost:8080/api/v1/channels/$CHANNEL_ID/messages \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $USER1_ID" \
  -d "{\"content\": \"$LONG_CONTENT\"}" | jq
```

**期待されるレスポンス**（400 Bad Request）

---

## 8. データベース確認

### 8.1 メッセージテーブル

```sql
SELECT 
    message_id,
    channel_id,
    user_id,
    LEFT(content, 50) as content_preview,
    created_at
FROM messages
ORDER BY created_at DESC;
```

### 8.2 チャンネルごとのメッセージ数

```sql
SELECT 
    c.channel_name,
    COUNT(m.message_id) as message_count
FROM channels c
LEFT JOIN messages m ON c.channel_id = m.channel_id
GROUP BY c.channel_id, c.channel_name;
```

### 8.3 ユーザーごとの投稿数

```sql
SELECT 
    u.username,
    u.display_name,
    COUNT(m.message_id) as post_count
FROM users u
LEFT JOIN messages m ON u.user_id = m.user_id
GROUP BY u.user_id, u.username, u.display_name
ORDER BY post_count DESC;
```

---

## 9. パフォーマンステスト

### 9.1 大量メッセージの送信

```bash
# 100件のメッセージを送信
for i in {1..100}; do
  curl -s -X POST http://localhost:8080/api/v1/channels/$CHANNEL_ID/messages \
    -H "Content-Type: application/json" \
    -H "X-User-Id: $USER1_ID" \
    -d "{\"content\": \"Test message number $i\"}" > /dev/null
  echo "Sent message $i"
done
```

### 9.2 ページネーション動作確認

```bash
# 最初の10件
curl -X GET "http://localhost:8080/api/v1/channels/$CHANNEL_ID/messages?limit=10&offset=0" \
  -H "X-User-Id: $USER1_ID" | jq '. | length'

# 次の10件
curl -X GET "http://localhost:8080/api/v1/channels/$CHANNEL_ID/messages?limit=10&offset=10" \
  -H "X-User-Id: $USER1_ID" | jq '. | length'
```

### 9.3 インデックスの確認

```sql
-- messagesテーブルのインデックス確認
\d messages

-- 実行計画の確認
EXPLAIN ANALYZE
SELECT * FROM messages
WHERE channel_id = '<channel-id>'
ORDER BY created_at DESC
LIMIT 10;
```

**学習ポイント**:
- ✅ **インデックス**: `idx_channel_created`で高速化
- ✅ **EXPLAIN ANALYZE**: クエリパフォーマンスの確認

---

## 10. メッセージ検索の実装（追加機能）

### 10.1 検索APIの実装

**ファイル**: `src/main/java/com/minislack/presentation/api/message/MessageSearchController.java`

```java
package com.minislack.presentation.api.message;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minislack.application.message.MessageService;
import com.minislack.domain.model.message.Message;
import com.minislack.domain.model.user.UserId;

/**
 * メッセージ検索REST APIコントローラー
 */
@RestController
@RequestMapping("/api/v1/messages")
public class MessageSearchController {
    
    private final MessageService messageService;

    public MessageSearchController(@NonNull MessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService);
    }

    /**
     * メッセージ検索
     * GET /api/v1/messages/search?q=keyword
     */
    @GetMapping("/search")
    @NonNull
    public ResponseEntity<List<MessageResponse>> searchMessages(
            @RequestParam("q") @NonNull String keyword,
            @RequestHeader("X-User-Id") @NonNull String currentUserId) {
        
        List<Message> messages = messageService.searchMessages(
            keyword,
            UserId.of(currentUserId)
        );

        List<MessageResponse> response = messages.stream()
            .map(MessageResponse::fromDomain)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}
```

### 10.2 検索テスト

```bash
curl -X GET "http://localhost:8080/api/v1/messages/search?q=first" \
  -H "X-User-Id: $USER1_ID" | jq
```

**期待されるレスポンス**:
```json
[
  {
    "messageId": "...",
    "channelId": "...",
    "userId": "...",
    "content": "Hello, everyone! This is my first message.",
    "createdAt": "2025-11-01T12:50:00"
  }
]
```

---

## 11. 統合テスト

### 11.1 MessageControllerのテスト

**ファイル**: `src/test/java/com/minislack/presentation/api/message/MessageControllerIntegrationTest.java`

```java
package com.minislack.presentation.api.message;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * メッセージコントローラー統合テスト
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MessageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String userId;
    private String channelId;

    @BeforeEach
    void setUp() throws Exception {
        // テストデータ作成（ユーザーとチャンネル）
        // 実際にはTestDataBuilderなどを使用してテストデータを準備
        
        // 例：
        // User testUser = TestDataBuilder.user()
        //     .username("testuser")
        //     .email("test@example.com")
        //     .build();
        // userRepository.save(testUser);
        // userId = testUser.getUserId().getValue();
        // 
        // Channel testChannel = TestDataBuilder.channel()
        //     .channelName("test-channel")
        //     .createdBy(userId)
        //     .build();
        // channelRepository.save(testChannel);
        // channelId = testChannel.getChannelId().getValue();
    }

    @Test
    void sendMessage_ValidRequest_Returns201Created() throws Exception {
        String requestBody = """
            {
              "content": "Test message"
            }
            """;

        mockMvc.perform(post("/api/v1/channels/" + channelId + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", userId)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.messageId").exists())
            .andExpect(jsonPath("$.content").value("Test message"));
    }

    @Test
    void getMessages_ValidRequest_ReturnsMessages() throws Exception {
        mockMvc.perform(get("/api/v1/channels/" + channelId + "/messages")
                .param("limit", "10")
                .param("offset", "0")
                .header("X-User-Id", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
}
```

---

## 12. まとめ

### 12.1 実装したもの

- ✅ ドメイン層: Message, MessageId, MessageContent
- ✅ アプリケーション層: MessageService
- ✅ インフラ層: MessageRepositoryImpl
- ✅ プレゼンテーション層: MessageController, MessageSearchController

### 12.2 動作確認した機能

- ✅ メッセージ送信（正常系・異常系）
- ✅ メッセージ一覧取得（ページネーション）
- ✅ メッセージ検索
- ✅ 認可チェック（メンバーのみ送信可能）

### 12.3 次のステップ

メッセージAPI機能が完成しました！次はRabbitMQを使ったリアルタイム通知を実装します：

- [13-realtime-notification.md](13-realtime-notification.md) - RabbitMQリアルタイム通知実装

---

## 13. よくある質問

### Q1. なぜメッセージ送信後に再取得しないのか？

**A**: 現在の実装では簡略化のため、送信時のデータをそのまま返しています。実際のアプリケーションでは、DBから再取得して返すのが推奨されます。

### Q2. ページネーションのoffsetは何？

**A**: 
- `limit=10, offset=0`: 最初の10件
- `limit=10, offset=10`: 11件目から20件目
- `offset`は「スキップする件数」

### Q3. 全文検索はどう実装するのか？

**A**: 現在の実装は`LIKE '%keyword%'`による簡易検索です。本格的な全文検索には：
- PostgreSQL Full Text Search
- Elasticsearch
などを使用します（応用編で実装）

---

## 14. 参考資料

- [Spring Data JPA - Pagination](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html#repositories.limit-query-result)
- [PostgreSQL Indexes](https://www.postgresql.org/docs/current/indexes.html)
- [RESTful API - Pagination](https://restfulapi.net/pagination/)

