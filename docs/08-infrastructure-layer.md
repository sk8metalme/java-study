# インフラ層実装ハンズオン

## 1. はじめに

このドキュメントでは、MiniSlackのインフラストラクチャ層を実装していきます。

### 1.1 インフラ層とは？

**インフラストラクチャ層**は、**技術的な詳細を実装**するレイヤーです。

**責務**:
- リポジトリインターフェースの実装（JPA/Hibernate）
- 外部システムとの連携（RabbitMQ、外部API等）
- セキュリティ実装（BCrypt等）

**特徴**:
- ドメイン層のインターフェースを実装（依存性逆転）
- フレームワークや技術スタックに依存
- ドメイン層と技術的詳細の橋渡し

---

## 2. ディレクトリ構造

```text
src/main/java/com/minislack/infrastructure/
├── persistence/       # データ永続化
│   ├── user/         # ユーザーリポジトリ実装
│   ├── channel/      # チャンネルリポジトリ実装
│   └── message/      # メッセージリポジトリ実装
├── security/         # セキュリティ実装
├── messaging/        # RabbitMQ実装
└── exception/        # インフラ例外
```

---

## 3. JPAエンティティの実装

### 3.1 JPAエンティティとドメインエンティティの違い

| 種類 | 目的 | 特徴 |
|-----|------|------|
| **ドメインエンティティ** | ビジネスロジック | フレームワーク非依存、リッチモデル |
| **JPAエンティティ** | DBマッピング | JPA/Hibernateに依存、アノテーション多用 |

**なぜ分けるのか？**
- ドメインの独立性を保つ
- データベーススキーマの変更がビジネスロジックに影響しない
- テストが容易

### 3.2 UserJpaEntity

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/user/UserJpaEntity.java`

```java
package com.minislack.infrastructure.persistence.user;

import java.time.LocalDateTime;

import org.springframework.lang.NonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ユーザーJPAエンティティ
 * データベーステーブルとのマッピング
 */
@Entity
@Table(name = "users")
public class UserJpaEntity {
    
    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "username", unique = true, nullable = false, length = 20)
    private String username;

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // デフォルトコンストラクタ（JPA必須）
    protected UserJpaEntity() {
    }

    // Getter / Setter
    @NonNull
    public String getUserId() {
        return userId;
    }

    public void setUserId(@NonNull String userId) {
        this.userId = userId;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    public void setUsername(@NonNull String username) {
        this.username = username;
    }

    @NonNull
    public String getEmail() {
        return email;
    }

    public void setEmail(@NonNull String email) {
        this.email = email;
    }

    @NonNull
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(@NonNull String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(@NonNull String displayName) {
        this.displayName = displayName;
    }

    @NonNull
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(@NonNull LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @NonNull
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(@NonNull LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
```

**学習ポイント**:
- ✅ **@Entity**: JPA管理対象
- ✅ **@Table**: テーブル名指定
- ✅ **@Id**: 主キー
- ✅ **@Column**: カラム定義（制約、長さ、null許可等）
- ✅ **protectedコンストラクタ**: JPAが使用（外部から直接作成不可）
- ✅ **Getter/Setter**: JPAが必要とする

---

## 4. マッパーの実装

### 4.1 UserEntityMapper

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/user/UserEntityMapper.java`

```java
package com.minislack.infrastructure.persistence.user;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.minislack.domain.model.user.DisplayName;
import com.minislack.domain.model.user.Email;
import com.minislack.domain.model.user.Password;
import com.minislack.domain.model.user.User;
import com.minislack.domain.model.user.UserId;
import com.minislack.domain.model.user.Username;

/**
 * ユーザーエンティティマッパー
 * ドメインエンティティ ⇔ JPAエンティティの変換
 */
@Component
public class UserEntityMapper {

    /**
     * ドメインエンティティ → JPAエンティティ
     */
    @NonNull
    public UserJpaEntity toJpaEntity(@NonNull User user) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.setUserId(user.getUserId().getValue());
        entity.setUsername(user.getUsername().getValue());
        entity.setEmail(user.getEmail().getValue());
        entity.setPasswordHash(user.getPassword().getHashedValue());
        entity.setDisplayName(user.getDisplayName().getValue());
        entity.setCreatedAt(user.getCreatedAt());
        entity.setUpdatedAt(user.getUpdatedAt());
        return entity;
    }

    /**
     * JPAエンティティ → ドメインエンティティ
     */
    @NonNull
    public User toDomain(@NonNull UserJpaEntity entity) {
        return new User(
            UserId.of(entity.getUserId()),
            new Username(entity.getUsername()),
            new Email(entity.getEmail()),
            Password.fromHashedValue(entity.getPasswordHash()),
            new DisplayName(entity.getDisplayName()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
```

**学習ポイント**:
- ✅ **@Component**: Springが管理
- ✅ **双方向変換**: `toJpaEntity()`と`toDomain()`
- ✅ **値の変換**: String ⇔ 値オブジェクト

**MapStructを使った実装例（推奨）**:

```java
package com.minislack.infrastructure.persistence.user;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.minislack.domain.model.user.User;

/**
 * MapStructを使った自動マッピング
 */
@Mapper(componentModel = "spring")
public interface UserEntityMapperMapStruct {
    
    @Mapping(target = "userId", expression = "java(user.getUserId().getValue())")
    @Mapping(target = "username", expression = "java(user.getUsername().getValue())")
    @Mapping(target = "email", expression = "java(user.getEmail().getValue())")
    @Mapping(target = "passwordHash", expression = "java(user.getPassword().getHashedValue())")
    @Mapping(target = "displayName", expression = "java(user.getDisplayName().getValue())")
    UserJpaEntity toJpaEntity(User user);
    
    // ... toDomainも同様
}
```

### MapStructとは？

**MapStruct**は、Javaのオブジェクト間マッピングを自動生成するコード生成ライブラリです。

**問題**: 手動マッピングは冗長で保守が大変
```java
// 手動マッピング（20行以上）
public UserJpaEntity toJpaEntity(User user) {
    UserJpaEntity entity = new UserJpaEntity();
    entity.setUserId(user.getUserId().getValue());
    entity.setUsername(user.getUsername().getValue());
    entity.setEmail(user.getEmail().getValue());
    // ... あと10個のフィールド
    return entity;
}
```

**解決**: MapStructで自動生成
```java
// インターフェースを定義するだけ
@Mapper(componentModel = "spring")
public interface UserEntityMapper {
    UserJpaEntity toJpaEntity(User user);
}
// ↑ コンパイル時に実装クラスが自動生成される
```

**メリット**:
- ✅ **ボイラープレートコード削減**: Getter/Setterの羅列が不要
- ✅ **タイプセーフ**: コンパイル時にチェック（リフレクション不使用）
- ✅ **高速**: 生成されたコードは手書きと同等のパフォーマンス
- ✅ **保守性**: フィールド追加時も自動対応

**デメリット**:
- △ 学習コスト: アノテーションの理解が必要
- △ 複雑な変換: 値オブジェクト変換は`expression`で手動記述

**このプロジェクトでの推奨**:
- **学習フェーズ**: 手動マッピング（仕組みを理解するため）
- **実践フェーズ**: MapStruct導入（効率化）

**依存関係**（build.gradleに追加済み）:
```groovy
implementation 'org.mapstruct:mapstruct:1.5.5.Final'
annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'
annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'
```

**参考資料**:
- [MapStruct公式サイト](https://mapstruct.org/)
- [MapStruct Reference Guide](https://mapstruct.org/documentation/stable/reference/html/)

---

## 5. リポジトリ実装

### 5.1 Spring Data JPA Repository

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/user/SpringDataUserRepository.java`

```java
package com.minislack.infrastructure.persistence.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

/**
 * Spring Data JPA Repository
 * JpaRepositoryを継承するだけで基本的なCRUD操作が使える
 */
interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, String> {
    
    @NonNull
    Optional<UserJpaEntity> findByEmail(@NonNull String email);
    
    @NonNull
    Optional<UserJpaEntity> findByUsername(@NonNull String username);
    
    boolean existsByEmail(@NonNull String email);
    
    boolean existsByUsername(@NonNull String username);
}
```

**学習ポイント**:
- ✅ **JpaRepository**: 基本的なCRUD操作を継承
- ✅ **メソッド命名規約**: `findBy...`, `existsBy...`で自動実装
- ✅ **package-private**: 外部から直接使用させない

### 5.2 UserRepositoryImpl

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/user/UserRepositoryImpl.java`

```java
package com.minislack.infrastructure.persistence.user;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.minislack.domain.model.user.Email;
import com.minislack.domain.model.user.IUserRepository;
import com.minislack.domain.model.user.User;
import com.minislack.domain.model.user.UserId;
import com.minislack.domain.model.user.Username;

/**
 * ユーザーリポジトリ実装
 * ドメインのIUserRepositoryインターフェースを実装
 */
@Repository
public class UserRepositoryImpl implements IUserRepository {
    private final SpringDataUserRepository jpaRepository;
    private final UserEntityMapper mapper;

    public UserRepositoryImpl(@NonNull SpringDataUserRepository jpaRepository, 
                             @NonNull UserEntityMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    @NonNull
    public User save(@NonNull User user) {
        Objects.requireNonNull(user, "user must not be null");
        UserJpaEntity entity = mapper.toJpaEntity(user);
        UserJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @NonNull
    public Optional<User> findById(@NonNull UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return jpaRepository.findById(userId.getValue())
                           .map(mapper::toDomain);
    }

    @Override
    @NonNull
    public Optional<User> findByEmail(@NonNull Email email) {
        Objects.requireNonNull(email, "email must not be null");
        return jpaRepository.findByEmail(email.getValue())
                           .map(mapper::toDomain);
    }

    @Override
    @NonNull
    public Optional<User> findByUsername(@NonNull Username username) {
        Objects.requireNonNull(username, "username must not be null");
        return jpaRepository.findByUsername(username.getValue())
                           .map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(@NonNull Email email) {
        Objects.requireNonNull(email, "email must not be null");
        return jpaRepository.existsByEmail(email.getValue());
    }

    @Override
    public boolean existsByUsername(@NonNull Username username) {
        Objects.requireNonNull(username, "username must not be null");
        return jpaRepository.existsByUsername(username.getValue());
    }

    @Override
    @NonNull
    public List<User> findAll() {
        return jpaRepository.findAll().stream()
                           .map(mapper::toDomain)
                           .collect(Collectors.toList());
    }
}
```

**学習ポイント**:
- ✅ **@Repository**: Springのリポジトリコンポーネント
- ✅ **ラッパーパターン**: Spring Data JPAをラップ
- ✅ **変換処理**: JPAエンティティ ⇔ ドメインエンティティ
- ✅ **Stream API**: `map()`でコレクション変換

**処理フロー**:
```text
1. ドメインエンティティを受け取る
2. JPAエンティティに変換
3. Spring Data JPAで永続化
4. 結果をドメインエンティティに変換
5. 返却
```

---

## 6. セキュリティ実装

### 6.1 BCryptPasswordEncoder

**ファイル**: `src/main/java/com/minislack/infrastructure/security/BCryptPasswordEncoderAdapter.java`

```java
package com.minislack.infrastructure.security;

import java.util.Objects;

import org.springframework.lang.NonNull;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.minislack.domain.model.user.IPasswordEncoder;

/**
 * BCryptパスワードエンコーダー実装
 * ドメインのIPasswordEncoderインターフェースを実装
 */
@Component
public class BCryptPasswordEncoderAdapter implements IPasswordEncoder {
    
    private final BCryptPasswordEncoder encoder;

    public BCryptPasswordEncoderAdapter() {
        this.encoder = new BCryptPasswordEncoder(10); // ストレングス10
    }

    @Override
    @NonNull
    public String encode(@NonNull String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(@NonNull String rawPassword, @NonNull String encodedPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        Objects.requireNonNull(encodedPassword, "encodedPassword must not be null");
        return encoder.matches(rawPassword, encodedPassword);
    }
}
```

**学習ポイント**:
- ✅ **Adapterパターン**: SpringのBCryptPasswordEncoderをドメインインターフェースに適合
- ✅ **ストレングス10**: セキュリティとパフォーマンスのバランス
- ✅ **依存性逆転**: ドメインがインフラに依存しない

---

## 7. チャンネルリポジトリの実装

### 7.1 ChannelJpaEntity

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/channel/ChannelJpaEntity.java`

```java
package com.minislack.infrastructure.persistence.channel;

import java.time.LocalDateTime;

import org.springframework.lang.NonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * チャンネルJPAエンティティ
 */
@Entity
@Table(name = "channels")
public class ChannelJpaEntity {
    
    @Id
    @Column(name = "channel_id", length = 36)
    private String channelId;

    @Column(name = "channel_name", unique = true, nullable = false, length = 50)
    private String channelName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ChannelJpaEntity() {
    }

    // Getter / Setter
    @NonNull
    public String getChannelId() { return channelId; }
    public void setChannelId(@NonNull String channelId) { this.channelId = channelId; }

    @NonNull
    public String getChannelName() { return channelName; }
    public void setChannelName(@NonNull String channelName) { this.channelName = channelName; }

    @NonNull
    public String getDescription() { return description; }
    public void setDescription(@NonNull String description) { this.description = description; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    @NonNull
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(@NonNull String createdBy) { this.createdBy = createdBy; }

    @NonNull
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(@NonNull LocalDateTime createdAt) { this.createdAt = createdAt; }

    @NonNull
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(@NonNull LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

### 7.2 ChannelMembershipJpaEntity

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/channel/ChannelMembershipJpaEntity.java`

```java
package com.minislack.infrastructure.persistence.channel;

import java.time.LocalDateTime;

import org.springframework.lang.NonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * チャンネルメンバーシップJPAエンティティ
 */
@Entity
@Table(name = "channel_memberships", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"channel_id", "user_id"}))
public class ChannelMembershipJpaEntity {
    
    @Id
    @Column(name = "membership_id", length = 36)
    private String membershipId;

    @Column(name = "channel_id", nullable = false, length = 36)
    private String channelId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    protected ChannelMembershipJpaEntity() {
    }

    // Getter / Setter
    @NonNull
    public String getMembershipId() { return membershipId; }
    public void setMembershipId(@NonNull String membershipId) { this.membershipId = membershipId; }

    @NonNull
    public String getChannelId() { return channelId; }
    public void setChannelId(@NonNull String channelId) { this.channelId = channelId; }

    @NonNull
    public String getUserId() { return userId; }
    public void setUserId(@NonNull String userId) { this.userId = userId; }

    @NonNull
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(@NonNull LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}
```

**学習ポイント**:
- ✅ **@UniqueConstraint**: 複合ユニーク制約（同じユーザーが同じチャンネルに複数回参加不可）

### 7.3 ChannelRepositoryImpl

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/channel/ChannelRepositoryImpl.java`

```java
package com.minislack.infrastructure.persistence.channel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.minislack.domain.model.channel.Channel;
import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.channel.ChannelName;
import com.minislack.domain.model.channel.IChannelRepository;
import com.minislack.domain.model.user.UserId;

/**
 * チャンネルリポジトリ実装
 */
@Repository
public class ChannelRepositoryImpl implements IChannelRepository {
    private final SpringDataChannelRepository jpaRepository;
    private final SpringDataChannelMembershipRepository membershipJpaRepository;
    private final ChannelEntityMapper mapper;

    public ChannelRepositoryImpl(@NonNull SpringDataChannelRepository jpaRepository,
                                 @NonNull SpringDataChannelMembershipRepository membershipJpaRepository,
                                 @NonNull ChannelEntityMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.membershipJpaRepository = Objects.requireNonNull(membershipJpaRepository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    @NonNull
    public Channel save(@NonNull Channel channel) {
        Objects.requireNonNull(channel, "channel must not be null");
        ChannelJpaEntity entity = mapper.toJpaEntity(channel);
        ChannelJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @NonNull
    public Optional<Channel> findById(@NonNull ChannelId channelId) {
        Objects.requireNonNull(channelId, "channelId must not be null");
        return jpaRepository.findById(channelId.getValue())
                           .map(mapper::toDomain);
    }

    @Override
    @NonNull
    public Optional<Channel> findByName(@NonNull ChannelName channelName) {
        Objects.requireNonNull(channelName, "channelName must not be null");
        return jpaRepository.findByChannelName(channelName.getValue())
                           .map(mapper::toDomain);
    }

    @Override
    @NonNull
    public List<Channel> findAllPublic() {
        return jpaRepository.findByIsPublicTrue().stream()
                           .map(mapper::toDomain)
                           .collect(Collectors.toList());
    }

    @Override
    @NonNull
    public List<Channel> findByMember(@NonNull UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        
        // メンバーシップから チャンネルIDを取得
        List<String> channelIds = membershipJpaRepository.findByUserId(userId.getValue())
            .stream()
            .map(ChannelMembershipJpaEntity::getChannelId)
            .collect(Collectors.toList());
        
        // チャンネルを取得
        return jpaRepository.findAllById(channelIds).stream()
                           .map(mapper::toDomain)
                           .collect(Collectors.toList());
    }

    @Override
    public boolean existsByName(@NonNull ChannelName channelName) {
        Objects.requireNonNull(channelName, "channelName must not be null");
        return jpaRepository.existsByChannelName(channelName.getValue());
    }
}
```

**Spring Data JPA Repository定義**:

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/channel/SpringDataChannelRepository.java`

```java
package com.minislack.infrastructure.persistence.channel;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

interface SpringDataChannelRepository extends JpaRepository<ChannelJpaEntity, String> {
    @NonNull
    Optional<ChannelJpaEntity> findByChannelName(@NonNull String channelName);
    
    @NonNull
    List<ChannelJpaEntity> findByIsPublicTrue();
    
    boolean existsByChannelName(@NonNull String channelName);
}
```

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/channel/SpringDataChannelMembershipRepository.java`

```java
package com.minislack.infrastructure.persistence.channel;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

interface SpringDataChannelMembershipRepository extends JpaRepository<ChannelMembershipJpaEntity, String> {
    @NonNull
    Optional<ChannelMembershipJpaEntity> findByChannelIdAndUserId(@NonNull String channelId, @NonNull String userId);
    
    @NonNull
    List<ChannelMembershipJpaEntity> findByChannelId(@NonNull String channelId);
    
    @NonNull
    List<ChannelMembershipJpaEntity> findByUserId(@NonNull String userId);
    
    boolean existsByChannelIdAndUserId(@NonNull String channelId, @NonNull String userId);
}
```

**学習ポイント**:
- ✅ **複雑なクエリ**: 複数のリポジトリを組み合わせて実装
- ✅ **Stream API**: データ変換の連鎖

---

## 8. メッセージリポジトリの実装

### 8.1 MessageJpaEntity

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/message/MessageJpaEntity.java`

```java
package com.minislack.infrastructure.persistence.message;

import java.time.LocalDateTime;

import org.springframework.lang.NonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * メッセージJPAエンティティ
 */
@Entity
@Table(name = "messages",
       indexes = {
           @Index(name = "idx_channel_created", columnList = "channel_id,created_at"),
           @Index(name = "idx_created_at", columnList = "created_at")
       })
public class MessageJpaEntity {
    
    @Id
    @Column(name = "message_id", length = 36)
    private String messageId;

    @Column(name = "channel_id", nullable = false, length = 36)
    private String channelId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected MessageJpaEntity() {
    }

    // Getter / Setter
    @NonNull
    public String getMessageId() { return messageId; }
    public void setMessageId(@NonNull String messageId) { this.messageId = messageId; }

    @NonNull
    public String getChannelId() { return channelId; }
    public void setChannelId(@NonNull String channelId) { this.channelId = channelId; }

    @NonNull
    public String getUserId() { return userId; }
    public void setUserId(@NonNull String userId) { this.userId = userId; }

    @NonNull
    public String getContent() { return content; }
    public void setContent(@NonNull String content) { this.content = content; }

    @NonNull
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(@NonNull LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

**学習ポイント**:
- ✅ **@Index**: パフォーマンス最適化のためのインデックス
- ✅ **複合インデックス**: `channel_id,created_at`で効率的な検索

### 8.2 MessageRepositoryImpl

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/message/MessageRepositoryImpl.java`

```java
package com.minislack.infrastructure.persistence.message;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.message.IMessageRepository;
import com.minislack.domain.model.message.Message;
import com.minislack.domain.model.message.MessageId;
import com.minislack.domain.model.user.UserId;

/**
 * メッセージリポジトリ実装
 */
@Repository
public class MessageRepositoryImpl implements IMessageRepository {
    private final SpringDataMessageRepository jpaRepository;
    private final MessageEntityMapper mapper;

    public MessageRepositoryImpl(@NonNull SpringDataMessageRepository jpaRepository,
                                @NonNull MessageEntityMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    @NonNull
    public Message save(@NonNull Message message) {
        Objects.requireNonNull(message, "message must not be null");
        MessageJpaEntity entity = mapper.toJpaEntity(message);
        MessageJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @NonNull
    public Optional<Message> findById(@NonNull MessageId messageId) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        return jpaRepository.findById(messageId.getValue())
                           .map(mapper::toDomain);
    }

    @Override
    @NonNull
    public List<Message> findByChannel(@NonNull ChannelId channelId, int limit, int offset) {
        Objects.requireNonNull(channelId, "channelId must not be null");
        
        PageRequest pageRequest = PageRequest.of(
            offset / limit, 
            limit, 
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        
        return jpaRepository.findByChannelId(channelId.getValue(), pageRequest)
                           .stream()
                           .map(mapper::toDomain)
                           .collect(Collectors.toList());
    }

    @Override
    @NonNull
    public List<Message> findByChannelAfter(@NonNull ChannelId channelId, @NonNull LocalDateTime after) {
        Objects.requireNonNull(channelId, "channelId must not be null");
        Objects.requireNonNull(after, "after must not be null");
        
        return jpaRepository.findByChannelIdAndCreatedAtAfter(channelId.getValue(), after)
                           .stream()
                           .map(mapper::toDomain)
                           .collect(Collectors.toList());
    }

    @Override
    @NonNull
    public List<Message> searchByKeyword(@NonNull String keyword, @NonNull UserId userId) {
        Objects.requireNonNull(keyword, "keyword must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        
        // TODO: ユーザーが参加しているチャンネルのみ検索
        return jpaRepository.findByContentContaining(keyword).stream()
                           .map(mapper::toDomain)
                           .collect(Collectors.toList());
    }

    @Override
    public long countByChannel(@NonNull ChannelId channelId) {
        Objects.requireNonNull(channelId, "channelId must not be null");
        return jpaRepository.countByChannelId(channelId.getValue());
    }

    @Override
    @NonNull
    public List<Message> findOlderThan(@NonNull LocalDateTime threshold) {
        Objects.requireNonNull(threshold, "threshold must not be null");
        return jpaRepository.findByCreatedAtBefore(threshold).stream()
                           .map(mapper::toDomain)
                           .collect(Collectors.toList());
    }

    @Override
    public void deleteByIds(@NonNull List<MessageId> messageIds) {
        Objects.requireNonNull(messageIds, "messageIds must not be null");
        List<String> ids = messageIds.stream()
                                     .map(MessageId::getValue)
                                     .collect(Collectors.toList());
        jpaRepository.deleteAllById(ids);
    }
}
```

**Spring Data JPA Repository**:

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/message/SpringDataMessageRepository.java`

```java
package com.minislack.infrastructure.persistence.message;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

interface SpringDataMessageRepository extends JpaRepository<MessageJpaEntity, String> {
    @NonNull
    List<MessageJpaEntity> findByChannelId(@NonNull String channelId, @NonNull Pageable pageable);
    
    @NonNull
    List<MessageJpaEntity> findByChannelIdAndCreatedAtAfter(@NonNull String channelId, @NonNull LocalDateTime after);
    
    @NonNull
    List<MessageJpaEntity> findByContentContaining(@NonNull String keyword);
    
    long countByChannelId(@NonNull String channelId);
    
    @NonNull
    List<MessageJpaEntity> findByCreatedAtBefore(@NonNull LocalDateTime threshold);
}
```

**学習ポイント**:
- ✅ **Pageable**: ページネーション対応
- ✅ **Sort**: ソート指定
- ✅ **メソッド命名規約**: `findBy...And...`, `countBy...`等

---

## 9. データベーススキーマ

### 9.1 テーブル設計

**users テーブル**:
```sql
CREATE TABLE users (
    user_id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(20) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_email ON users(email);
CREATE INDEX idx_username ON users(username);
```

**channels テーブル**:
```sql
CREATE TABLE channels (
    channel_id VARCHAR(36) PRIMARY KEY,
    channel_name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(500),
    is_public BOOLEAN NOT NULL DEFAULT true,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (created_by) REFERENCES users(user_id)
);

CREATE INDEX idx_channel_name ON channels(channel_name);
CREATE INDEX idx_is_public ON channels(is_public);
```

**channel_memberships テーブル**:
```sql
CREATE TABLE channel_memberships (
    membership_id VARCHAR(36) PRIMARY KEY,
    channel_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    FOREIGN KEY (channel_id) REFERENCES channels(channel_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE(channel_id, user_id)
);

CREATE INDEX idx_channel_id ON channel_memberships(channel_id);
CREATE INDEX idx_user_id ON channel_memberships(user_id);
```

**messages テーブル**:
```sql
CREATE TABLE messages (
    message_id VARCHAR(36) PRIMARY KEY,
    channel_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (channel_id) REFERENCES channels(channel_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE INDEX idx_channel_created ON messages(channel_id, created_at DESC);
CREATE INDEX idx_created_at ON messages(created_at);
```

**学習ポイント**:
- ✅ **インデックス**: 検索パフォーマンス向上
- ✅ **外部キー制約**: データ整合性
- ✅ **ON DELETE CASCADE**: 関連データの自動削除

### 9.2 Hibernateによる自動生成

`application.yml`で`spring.jpa.hibernate.ddl-auto: update`を設定しているため、
アプリケーション起動時に自動的にテーブルが作成されます。

**環境別の推奨設定**:

| 環境 | ddl-auto設定 | 説明 |
|-----|-------------|------|
| **開発** | `create-drop` | 起動時にテーブル作成、停止時に削除（自動リセット） |
| **テスト** | `create-drop` | テストごとに自動リセット |
| **ステージング** | `validate` | スキーマ検証のみ（変更は手動マイグレーション） |
| **本番** | `none` または `validate` | Hibernateによるスキーマ変更を禁止 |

**本番環境のベストプラクティス**:
- `ddl-auto: none`または`validate`を使用
- **Flyway**や**Liquibase**などのマイグレーションツールを使用
- スキーマ変更はバージョン管理下のマイグレーションスクリプトで実施

**Flyway使用例**:
```yaml
# application-prod.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # スキーマ検証のみ
  flyway:
    enabled: true
    locations: classpath:db/migration
```

---

## 10. まとめ

### 10.1 インフラ層実装の要点

1. **JPAエンティティ**:
   - `@Entity`, `@Table`, `@Column`等のアノテーション
   - protectedコンストラクタ
   - Getter/Setter

2. **マッパー**:
   - ドメインエンティティ ⇔ JPAエンティティ
   - `@Component`で Spring管理

3. **リポジトリ実装**:
   - ドメインのインターフェースを実装
   - Spring Data JPAをラップ
   - `@Repository`アノテーション

4. **依存性逆転**:
   - ドメインがインターフェースを定義
   - インフラがそれを実装

### 10.2 次のステップ

インフラ層の実装が完了しました！次はプレゼンテーション層を実装します：

- [09-presentation-layer.md](09-presentation-layer.md) - プレゼンテーション層実装

---

## 11. 参考資料

- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Hibernate ORM](https://hibernate.org/orm/)
- [JPA Specifications](https://jakarta.ee/specifications/persistence/)

