# チャンネル管理機能実装ハンズオン

## 1. はじめに

このドキュメントでは、チャンネル管理機能を実装します。

### 1.1 実装する機能

- チャンネル作成API
- チャンネル一覧取得API
- チャンネル参加/退出API
- メンバー一覧取得API

### 1.2 前提条件

ユーザー管理機能（`10-user-management.md`）が実装済みであること。

---

## 2. Step 1: ドメイン層の実装

### 2.1 チャンネル関連の値オブジェクト

`06-domain-layer.md`を参照して、以下のファイルを作成してください：

1. `ChannelId.java`
2. `ChannelName.java`
3. `Description.java`
4. `MembershipId.java`

### 2.2 エンティティとリポジトリ

1. `Channel.java`
2. `IChannelRepository.java`
3. `ChannelMembership.java`
4. `IChannelMembershipRepository.java`

### 2.3 ドメインサービス

```bash
mkdir -p src/main/java/com/minislack/domain/service
```

**ファイル**: `src/main/java/com/minislack/domain/service/ChannelMembershipService.java`

（`06-domain-layer.md`を参照）

---

## 3. Step 2: アプリケーション層の実装

### 3.1 コマンドオブジェクト

```bash
mkdir -p src/main/java/com/minislack/application/channel
```

**ファイル**: `src/main/java/com/minislack/application/channel/CreateChannelCommand.java`

（`07-application-layer.md`を参照）

### 3.2 アプリケーションサービス

**ファイル**: `src/main/java/com/minislack/application/channel/ChannelManagementService.java`

（`07-application-layer.md`を参照）

---

## 4. Step 3: インフラ層の実装

### 4.1 JPAエンティティ

```bash
mkdir -p src/main/java/com/minislack/infrastructure/persistence/channel
```

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/channel/ChannelJpaEntity.java`

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/channel/ChannelMembershipJpaEntity.java`

（`08-infrastructure-layer.md`を参照）

### 4.2 マッパー

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/channel/ChannelEntityMapper.java`

```java
package com.minislack.infrastructure.persistence.channel;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.minislack.domain.model.channel.Channel;
import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.channel.ChannelName;
import com.minislack.domain.model.channel.Description;
import com.minislack.domain.model.user.UserId;

/**
 * チャンネルエンティティマッパー
 */
@Component
public class ChannelEntityMapper {

    @NonNull
    public ChannelJpaEntity toJpaEntity(@NonNull Channel channel) {
        ChannelJpaEntity entity = new ChannelJpaEntity();
        entity.setChannelId(channel.getChannelId().getValue());
        entity.setChannelName(channel.getChannelName().getValue());
        entity.setDescription(channel.getDescription().getValue());
        entity.setPublic(channel.isPublic());
        entity.setCreatedBy(channel.getCreatedBy().getValue());
        entity.setCreatedAt(channel.getCreatedAt());
        entity.setUpdatedAt(channel.getUpdatedAt());
        return entity;
    }

    @NonNull
    public Channel toDomain(@NonNull ChannelJpaEntity entity) {
        return new Channel(
            ChannelId.of(entity.getChannelId()),
            new ChannelName(entity.getChannelName()),
            new Description(entity.getDescription()),
            entity.isPublic(),
            UserId.of(entity.getCreatedBy()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
```

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/channel/ChannelMembershipEntityMapper.java`

```java
package com.minislack.infrastructure.persistence.channel;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.channel.ChannelMembership;
import com.minislack.domain.model.channel.MembershipId;
import com.minislack.domain.model.user.UserId;

/**
 * チャンネルメンバーシップエンティティマッパー
 */
@Component
public class ChannelMembershipEntityMapper {

    @NonNull
    public ChannelMembershipJpaEntity toJpaEntity(@NonNull ChannelMembership membership) {
        ChannelMembershipJpaEntity entity = new ChannelMembershipJpaEntity();
        entity.setMembershipId(membership.getMembershipId().getValue());
        entity.setChannelId(membership.getChannelId().getValue());
        entity.setUserId(membership.getUserId().getValue());
        entity.setJoinedAt(membership.getJoinedAt());
        return entity;
    }

    @NonNull
    public ChannelMembership toDomain(@NonNull ChannelMembershipJpaEntity entity) {
        return new ChannelMembership(
            MembershipId.of(entity.getMembershipId()),
            ChannelId.of(entity.getChannelId()),
            UserId.of(entity.getUserId()),
            entity.getJoinedAt()
        );
    }
}
```

### 4.3 Spring Data JPA Repository

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/channel/SpringDataChannelRepository.java`

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/channel/SpringDataChannelMembershipRepository.java`

（`08-infrastructure-layer.md`を参照）

### 4.4 リポジトリ実装

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/channel/ChannelRepositoryImpl.java`

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/channel/ChannelMembershipRepositoryImpl.java`

```java
package com.minislack.infrastructure.persistence.channel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.channel.ChannelMembership;
import com.minislack.domain.model.channel.IChannelMembershipRepository;
import com.minislack.domain.model.channel.MembershipId;
import com.minislack.domain.model.user.UserId;

/**
 * チャンネルメンバーシップリポジトリ実装
 */
@Repository
public class ChannelMembershipRepositoryImpl implements IChannelMembershipRepository {
    private final SpringDataChannelMembershipRepository jpaRepository;
    private final ChannelMembershipEntityMapper mapper;

    public ChannelMembershipRepositoryImpl(
            @NonNull SpringDataChannelMembershipRepository jpaRepository,
            @NonNull ChannelMembershipEntityMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    @NonNull
    public ChannelMembership save(@NonNull ChannelMembership membership) {
        Objects.requireNonNull(membership);
        ChannelMembershipJpaEntity entity = mapper.toJpaEntity(membership);
        ChannelMembershipJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @NonNull
    public Optional<ChannelMembership> findById(@NonNull MembershipId membershipId) {
        Objects.requireNonNull(membershipId);
        return jpaRepository.findById(membershipId.getValue())
                           .map(mapper::toDomain);
    }

    @Override
    @NonNull
    public Optional<ChannelMembership> findByChannelAndUser(@NonNull ChannelId channelId, 
                                                            @NonNull UserId userId) {
        Objects.requireNonNull(channelId);
        Objects.requireNonNull(userId);
        return jpaRepository.findByChannelIdAndUserId(channelId.getValue(), userId.getValue())
                           .map(mapper::toDomain);
    }

    @Override
    @NonNull
    public List<ChannelMembership> findByChannel(@NonNull ChannelId channelId) {
        Objects.requireNonNull(channelId);
        return jpaRepository.findByChannelId(channelId.getValue()).stream()
                           .map(mapper::toDomain)
                           .collect(Collectors.toList());
    }

    @Override
    @NonNull
    public List<ChannelMembership> findByUser(@NonNull UserId userId) {
        Objects.requireNonNull(userId);
        return jpaRepository.findByUserId(userId.getValue()).stream()
                           .map(mapper::toDomain)
                           .collect(Collectors.toList());
    }

    @Override
    public void delete(@NonNull ChannelMembership membership) {
        Objects.requireNonNull(membership);
        ChannelMembershipJpaEntity entity = mapper.toJpaEntity(membership);
        jpaRepository.delete(entity);
    }

    @Override
    public boolean existsByChannelAndUser(@NonNull ChannelId channelId, @NonNull UserId userId) {
        Objects.requireNonNull(channelId);
        Objects.requireNonNull(userId);
        return jpaRepository.existsByChannelIdAndUserId(channelId.getValue(), userId.getValue());
    }
}
```

---

## 5. Step 4: プレゼンテーション層の実装

### 5.1 DTO

```bash
mkdir -p src/main/java/com/minislack/presentation/api/channel
```

**ファイル**: `src/main/java/com/minislack/presentation/api/channel/CreateChannelRequest.java`

**ファイル**: `src/main/java/com/minislack/presentation/api/channel/ChannelResponse.java`

（`09-presentation-layer.md`を参照）

### 5.2 コントローラー

**ファイル**: `src/main/java/com/minislack/presentation/api/channel/ChannelController.java`

（`09-presentation-layer.md`を参照）

---

## 6. Step 5: 動作確認

### 6.1 チャンネル作成テスト

まず、ユーザーを登録してユーザーIDを取得します：

```bash
# ユーザー登録
USER_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "password123",
    "displayName": "Alice"
  }')

# ユーザーIDを抽出
USER_ID=$(echo $USER_RESPONSE | jq -r '.userId')
echo "User ID: $USER_ID"
```

チャンネルを作成：

```bash
curl -X POST http://localhost:8080/api/v1/channels \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $USER_ID" \
  -d '{
    "channelName": "general",
    "description": "General discussion",
    "isPublic": true
  }' | jq
```

**期待されるレスポンス**:
```json
{
  "channelId": "650e8400-e29b-41d4-a716-446655440000",
  "channelName": "general",
  "description": "General discussion",
  "isPublic": true,
  "createdBy": "550e8400-e29b-41d4-a716-446655440000",
  "createdAt": "2025-11-01T12:40:00"
}
```

### 6.2 公開チャンネル一覧取得

```bash
curl -X GET http://localhost:8080/api/v1/channels | jq
```

**期待されるレスポンス**:
```json
[
  {
    "channelId": "650e8400-e29b-41d4-a716-446655440000",
    "channelName": "general",
    "description": "General discussion",
    "isPublic": true,
    "createdBy": "550e8400-e29b-41d4-a716-446655440000",
    "createdAt": "2025-11-01T12:40:00"
  }
]
```

### 6.3 チャンネル参加

別のユーザーを作成：

```bash
USER2_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "bob",
    "email": "bob@example.com",
    "password": "password123",
    "displayName": "Bob"
  }')

USER2_ID=$(echo $USER2_RESPONSE | jq -r '.userId')
echo "User 2 ID: $USER2_ID"
```

チャンネルに参加：

```bash
CHANNEL_ID="<上で作成したチャンネルID>"

curl -X POST http://localhost:8080/api/v1/channels/$CHANNEL_ID/join \
  -H "X-User-Id: $USER2_ID"
```

**期待されるレスポンス**: 200 OK（ボディなし）

### 6.4 チャンネル退出

```bash
curl -X POST http://localhost:8080/api/v1/channels/$CHANNEL_ID/leave \
  -H "X-User-Id: $USER2_ID"
```

---

## 7. データベース確認

### 7.1 チャンネルテーブル

```sql
SELECT 
    channel_id, 
    channel_name, 
    description,
    is_public,
    created_by,
    created_at
FROM channels;
```

### 7.2 メンバーシップテーブル

```sql
SELECT 
    membership_id,
    channel_id,
    user_id,
    joined_at
FROM channel_memberships;
```

### 7.3 作成者が自動的にメンバーになっているか確認

```sql
SELECT 
    c.channel_name,
    u.username,
    cm.joined_at
FROM channel_memberships cm
JOIN channels c ON cm.channel_id = c.channel_id
JOIN users u ON cm.user_id = u.user_id
WHERE c.channel_name = 'general';
```

---

## 8. エラーケースのテスト

### 8.1 重複チャンネル名

```bash
curl -X POST http://localhost:8080/api/v1/channels \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $USER_ID" \
  -d '{
    "channelName": "general",
    "description": "Another general",
    "isPublic": true
  }' | jq
```

**期待されるレスポンス**（409 Conflict）:
```json
{
  "status": 409,
  "error": "Duplicate Resource",
  "message": "Channel name already exists: general",
  "timestamp": "2025-11-01T12:45:00"
}
```

### 8.2 存在しないチャンネルへの参加

```bash
curl -X POST http://localhost:8080/api/v1/channels/invalid-channel-id/join \
  -H "X-User-Id: $USER_ID" | jq
```

**期待されるレスポンス**（404 Not Found）:
```json
{
  "status": 404,
  "error": "Resource Not Found",
  "message": "Channel with id 'invalid-channel-id' not found",
  "timestamp": "2025-11-01T12:46:00"
}
```

### 8.3 バリデーションエラー

```bash
# チャンネル名が短すぎる（4文字未満）
curl -X POST http://localhost:8080/api/v1/channels \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $USER_ID" \
  -d '{
    "channelName": "abc",
    "description": "Test",
    "isPublic": true
  }' | jq
```

**期待されるレスポンス**（400 Bad Request）:
```json
{
  "status": 400,
  "error": "Validation Failed",
  "fieldErrors": {
    "channelName": "Channel name must be 4-50 characters"
  },
  "timestamp": "2025-11-01T12:47:00"
}
```

---

## 9. テストの実装

### 9.1 ChannelManagementServiceのテスト

**ファイル**: `src/test/java/com/minislack/application/channel/ChannelManagementServiceTest.java`

```java
package com.minislack.application.channel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.minislack.domain.exception.DuplicateResourceException;
import com.minislack.domain.model.channel.Channel;
import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.channel.ChannelName;
import com.minislack.domain.model.channel.IChannelMembershipRepository;
import com.minislack.domain.model.channel.IChannelRepository;
import com.minislack.domain.service.ChannelMembershipService;

class ChannelManagementServiceTest {
    
    private IChannelRepository channelRepository;
    private IChannelMembershipRepository membershipRepository;
    private ChannelMembershipService membershipService;
    private ChannelManagementService service;

    @BeforeEach
    void setUp() {
        channelRepository = mock(IChannelRepository.class);
        membershipRepository = mock(IChannelMembershipRepository.class);
        membershipService = mock(ChannelMembershipService.class);
        service = new ChannelManagementService(
            channelRepository, 
            membershipRepository, 
            membershipService
        );
    }

    @Test
    void createChannel_ValidCommand_ReturnsChannelId() {
        // Given
        CreateChannelCommand command = new CreateChannelCommand(
            "test-channel",
            "Test Description",
            true,
            "user-id-123"
        );
        
        when(channelRepository.existsByName(any(ChannelName.class))).thenReturn(false);
        
        Channel mockChannel = mock(Channel.class);
        when(mockChannel.getChannelId()).thenReturn(ChannelId.newId());
        when(channelRepository.save(any(Channel.class))).thenReturn(mockChannel);
        
        // When
        ChannelId channelId = service.createChannel(command);
        
        // Then
        assertNotNull(channelId);
        verify(channelRepository).save(any(Channel.class));
        verify(membershipRepository).save(any());
    }

    @Test
    void createChannel_DuplicateName_ThrowsException() {
        // Given
        CreateChannelCommand command = new CreateChannelCommand(
            "test-channel",
            "Test Description",
            true,
            "user-id-123"
        );
        
        when(channelRepository.existsByName(any(ChannelName.class))).thenReturn(true);
        
        // When & Then
        DuplicateResourceException exception = assertThrows(
            DuplicateResourceException.class,
            () -> service.createChannel(command)
        );
        
        assertEquals("Channel name already exists: test-channel", exception.getMessage());
    }
}
```

---

## 10. まとめ

### 10.1 実装したもの

- ✅ ドメイン層: Channel, ChannelMembership関連
- ✅ アプリケーション層: ChannelManagementService
- ✅ インフラ層: ChannelRepositoryImpl等
- ✅ プレゼンテーション層: ChannelController
- ✅ ドメインサービス: ChannelMembershipService

### 10.2 動作確認した機能

- ✅ チャンネル作成（正常系・異常系）
- ✅ 公開チャンネル一覧取得
- ✅ チャンネル参加/退出
- ✅ 作成者の自動メンバー追加

### 10.3 次のステップ

チャンネル管理機能が完成しました！次はメッセージ機能を実装します：

- [12-message-api.md](12-message-api.md) - メッセージAPI実装

---

## 11. 参考資料

- [Spring Data JPA - Query Methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html)
- [PostgreSQL Foreign Keys](https://www.postgresql.org/docs/current/ddl-constraints.html#DDL-CONSTRAINTS-FK)

