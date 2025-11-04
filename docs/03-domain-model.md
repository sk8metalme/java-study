# ドメインモデル設計

## 1. はじめに

このドキュメントでは、MiniSlackのドメインモデルを設計します。ドメインモデルとは、**ビジネスの中核概念をオブジェクト指向で表現したもの**です。

### 1.1 ドメイン駆動設計（DDD）の基礎

**ドメイン駆動設計（Domain-Driven Design, DDD）**は、Eric Evansが提唱した設計手法です。

**重要な概念**:
- **エンティティ（Entity）**: 識別子を持つオブジェクト
- **値オブジェクト（Value Object）**: 不変で等価性で比較されるオブジェクト
- **集約（Aggregate）**: 整合性境界を持つエンティティの集まり
- **リポジトリ（Repository）**: 集約の永続化と取得
- **ドメインサービス（Domain Service）**: エンティティに属さないビジネスロジック

---

## 2. MiniSlackのドメイン概念

### 2.1 ユビキタス言語（Ubiquitous Language）

チーム全員が共通して使う用語を定義します。

| 用語 | 英語 | 説明 |
|-----|------|------|
| ユーザー | User | システムを利用する人 |
| チャンネル | Channel | メッセージのやり取りをする場所 |
| メッセージ | Message | ユーザーがチャンネルに投稿する文章 |
| メンバーシップ | Membership | ユーザーとチャンネルの関連 |
| ワークスペース | Workspace | （将来拡張用）組織単位 |

### 2.2 境界づけられたコンテキスト（Bounded Context）

MiniSlackは以下のコンテキストに分割できます：

```
┌─────────────────────────────────────┐
│  User Context                       │
│  - ユーザー登録                     │
│  - 認証・認可                       │
│  - プロフィール管理                 │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  Channel Context                    │
│  - チャンネル作成                   │
│  - メンバー管理                     │
│  - 参加/退出                        │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  Message Context                    │
│  - メッセージ送信                   │
│  - メッセージ取得                   │
│  - メッセージ検索                   │
└─────────────────────────────────────┘
```

学習のため、今回はシンプルに1つのコンテキストにまとめます。

---

## 3. エンティティ設計

### 3.1 User（ユーザー）

**責務**: ユーザーの識別とプロフィール情報の管理

**属性**:
- `userId`: ユーザーID（識別子）
- `username`: ユーザー名（ログイン用、一意）
- `email`: メールアドレス（一意）
- `password`: パスワード（ハッシュ化済み）
- `displayName`: 表示名
- `createdAt`: 作成日時
- `updatedAt`: 更新日時

**ビジネスルール**:
- ユーザー名は3-20文字、英数字とアンダースコアのみ
- メールアドレスは有効な形式
- パスワードは8文字以上
- 表示名は1-50文字

**実装例**:

```java
package com.minislack.domain.model.user;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.lang.NonNull;

public class User {
    private final UserId userId;
    private Username username;
    private Email email;
    private Password password;
    private DisplayName displayName;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 新規ユーザー作成用コンストラクタ
    public User(@NonNull UserId userId, @NonNull Username username, @NonNull Email email, 
                @NonNull Password password, @NonNull DisplayName displayName) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.password = Objects.requireNonNull(password, "password must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 既存ユーザー復元用コンストラクタ（リポジトリから取得時）
    public User(@NonNull UserId userId, @NonNull Username username, @NonNull Email email, 
                @NonNull Password password, @NonNull DisplayName displayName,
                @NonNull LocalDateTime createdAt, @NonNull LocalDateTime updatedAt) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.password = Objects.requireNonNull(password, "password must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    // ビジネスロジック: パスワード変更
    public void changePassword(Password currentPassword, Password newPassword) {
        if (!this.password.equals(currentPassword)) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        this.password = newPassword;
        this.updatedAt = LocalDateTime.now();
    }

    // ビジネスロジック: プロフィール更新
    public void updateProfile(DisplayName newDisplayName) {
        this.displayName = newDisplayName;
        this.updatedAt = LocalDateTime.now();
    }

    // ゲッター
    public UserId getUserId() { return userId; }
    public Username getUsername() { return username; }
    public Email getEmail() { return email; }
    public Password getPassword() { return password; }
    public DisplayName getDisplayName() { return displayName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(userId, user.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
```

#### なぜコンストラクタを分けるのか？

**2つのコンストラクタを分ける理由**を詳しく説明します。

##### 1. 異なるライフサイクル（生成 vs 復元）

```java
// パターン1: 新規作成（アプリケーション層）
User newUser = new User(
    UserId.newId(),
    new Username("taro"),
    new Email("taro@example.com"),
    password,
    new DisplayName("太郎")
);
// → createdAt, updatedAt は「今」になる

// パターン2: 復元（インフラ層・リポジトリから）
User existingUser = new User(
    UserId.of("abc-123"),
    new Username("taro"),
    new Email("taro@example.com"),
    password,
    new DisplayName("太郎"),
    LocalDateTime.of(2024, 1, 1, 10, 0, 0),  // DBに保存されていた作成日時
    LocalDateTime.of(2024, 6, 15, 14, 30, 0) // DBに保存されていた更新日時
);
// → 元の日時を保持する
```

##### 2. 不変条件（Invariant）の保護

**新規作成時のルール**:
- ✅ 作成日時 = 現在時刻
- ✅ 更新日時 = 現在時刻
- ✅ 作成日時 = 更新日時（新規作成なので同じ）

**復元時のルール**:
- ✅ 元の日時をそのまま保持
- ✅ createdAt ≤ updatedAt が保証される

##### 3. 採用しなかった代替案とその理由

**代替案: createdAt/updatedAtのnullチェックで判定**

```java
// ❌ この方法は採用していません
public User(UserId userId, Username username, Email email, 
            Password password, DisplayName displayName,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
    this.userId = Objects.requireNonNull(userId);
    this.username = Objects.requireNonNull(username);
    this.email = Objects.requireNonNull(email);
    this.password = Objects.requireNonNull(password);
    this.displayName = Objects.requireNonNull(displayName);
    
    // createdAt が null なら新規作成と判定
    if (createdAt == null || updatedAt == null) {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    } else {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
```

**採用しない理由**:

**理由1: Null許容の問題（@NonNull契約を破る）**

```java
// ドメインモデルは原則すべて @NonNull
private final LocalDateTime createdAt; // null許容すべきではない

// しかし、nullチェック方式だとnullを受け入れる必要がある
public User(..., @Nullable LocalDateTime createdAt, ...) // ← 妥協が必要
```

**理由2: 型システムで保証できない（コンパイル時検証不可）**

```java
// コンパイルは通るが、意図しない呼び出しを防げない
User user = new User(userId, username, email, password, displayName, null, null);
// → 新規作成のつもりだが、明示的ではない
```

**理由3: 隠れたロジック（コンストラクタ内に分岐）**

```java
// コンストラクタ内に「if」がある = 複雑性の増加
// ドメインモデルはシンプルに保つべき
if (createdAt == null || updatedAt == null) {
    // 隠れた振る舞い
}
```

**理由4: 意図が不明確**

```java
// ❌ どちらのパターンか一目で分からない
User user = new User(userId, username, email, password, displayName, null, null);

// ✅ コンストラクタの引数の数で意図が明確
User newUser = new User(userId, username, email, password, displayName);
User existingUser = new User(userId, username, email, password, displayName, 
                             createdAt, updatedAt);
```

**理由5: エラーが遅延（実行時まで問題が分からない）**

```java
// コンパイルは通るが、実行時に問題が発生する可能性
User user = new User(userId, username, email, password, displayName, 
                     null, LocalDateTime.now()); // createdAtだけnull
// → updatedAtだけ設定されるバグが混入しやすい
```

**理由6: ドメインモデルの純粋性**

```java
// ドメインモデルは「ビジネスルール」を表現すべき
// 「nullチェック」は技術的詳細であり、ビジネスロジックではない

// ✅ ビジネスの言葉で表現
User newUser = new User(...); // 「新規ユーザーを作成する」
User existingUser = new User(..., createdAt, updatedAt); // 「既存ユーザーを復元する」
```

##### 4. 実際の使用例

**アプリケーション層（新規作成）**:
```java
@Service
public class UserRegistrationService {
    @Transactional
    public UserId registerUser(RegisterUserCommand command) {
        // 新規作成用コンストラクタ（意図が明確）
        User user = new User(
            UserId.newId(),
            new Username(command.getUsername()),
            new Email(command.getEmail()),
            Password.fromRawPassword(command.getPassword(), encoder),
            new DisplayName(command.getDisplayName())
        );
        
        return userRepository.save(user).getUserId();
    }
}
```

**インフラ層（復元）**:
```java
@Repository
public class UserRepositoryImpl implements IUserRepository {
    @Override
    public Optional<User> findById(UserId userId) {
        return jpaRepository.findById(userId.getValue())
            .map(entity -> new User(
                UserId.of(entity.getId()),
                new Username(entity.getUsername()),
                new Email(entity.getEmail()),
                Password.fromHashedValue(entity.getPassword()),
                new DisplayName(entity.getDisplayName()),
                entity.getCreatedAt(),  // DBから取得した値を保持
                entity.getUpdatedAt()   // DBから取得した値を保持
            ));
    }
}
```

##### 5. まとめ: コンストラクタ分離のメリット

| 観点 | 2つのコンストラクタ | nullチェック方式 |
|-----|------------------|----------------|
| **Null安全性** | ✅ すべて@NonNull | ❌ nullを許容 |
| **意図の明確性** | ✅ 一目で分かる | ❌ 不明確 |
| **コンパイル時検証** | ✅ 型で保証 | ❌ 実行時まで不明 |
| **シンプルさ** | ✅ 分岐なし | ❌ 内部に分岐 |
| **ドメインの純粋性** | ✅ Pure Java | ❌ 技術的詳細が混入 |

**結論**: MiniSlackでは、**型安全性**、**意図の明確性**、**ドメインモデルの純粋性**を優先し、2つのコンストラクタを使用します。

### 3.2 Channel（チャンネル）

**責務**: チャンネルの管理とメンバーシップの制御

**属性**:
- `channelId`: チャンネルID（識別子）
- `channelName`: チャンネル名（一意）
- `description`: 説明
- `isPublic`: 公開/非公開フラグ
- `createdBy`: 作成者（UserId）
- `createdAt`: 作成日時
- `updatedAt`: 更新日時

**ビジネスルール**:
- チャンネル名は4-50文字
- 説明は500文字以内
- 作成者は自動的にメンバーになる

**実装例**:

```java
package com.minislack.domain.model.channel;

import com.minislack.domain.model.user.UserId;
import java.time.LocalDateTime;
import java.util.Objects;

public class Channel {
    private final ChannelId channelId;
    private ChannelName channelName;
    private Description description;
    private final boolean isPublic;
    private final UserId createdBy;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 新規チャンネル作成用
    public Channel(ChannelId channelId, ChannelName channelName, 
                   Description description, boolean isPublic, UserId createdBy) {
        this.channelId = Objects.requireNonNull(channelId);
        this.channelName = Objects.requireNonNull(channelName);
        this.description = description;
        this.isPublic = isPublic;
        this.createdBy = Objects.requireNonNull(createdBy);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 既存チャンネル復元用
    public Channel(ChannelId channelId, ChannelName channelName, 
                   Description description, boolean isPublic, UserId createdBy,
                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.channelId = Objects.requireNonNull(channelId);
        this.channelName = Objects.requireNonNull(channelName);
        this.description = description;
        this.isPublic = isPublic;
        this.createdBy = Objects.requireNonNull(createdBy);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    // ビジネスロジック: チャンネル情報更新
    public void updateInfo(ChannelName newName, Description newDescription) {
        this.channelName = newName;
        this.description = newDescription;
        this.updatedAt = LocalDateTime.now();
    }

    // ビジネスロジック: 参加可能か判定
    public boolean canJoin() {
        return this.isPublic;
    }

    // ゲッター
    public ChannelId getChannelId() { return channelId; }
    public ChannelName getChannelName() { return channelName; }
    public Description getDescription() { return description; }
    public boolean isPublic() { return isPublic; }
    public UserId getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Channel channel = (Channel) o;
        return Objects.equals(channelId, channel.channelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId);
    }
}
```

### 3.3 ChannelMembership（チャンネルメンバーシップ）

**責務**: ユーザーとチャンネルの関連を管理

**属性**:
- `membershipId`: メンバーシップID（識別子）
- `channelId`: チャンネルID
- `userId`: ユーザーID
- `joinedAt`: 参加日時

**ビジネスルール**:
- 同じユーザーが同じチャンネルに複数回参加できない
- 非公開チャンネルには招待が必要（将来拡張）

**実装例**:

```java
package com.minislack.domain.model.channel;

import com.minislack.domain.model.user.UserId;
import java.time.LocalDateTime;
import java.util.Objects;

public class ChannelMembership {
    private final MembershipId membershipId;
    private final ChannelId channelId;
    private final UserId userId;
    private final LocalDateTime joinedAt;

    public ChannelMembership(MembershipId membershipId, ChannelId channelId, UserId userId) {
        this.membershipId = Objects.requireNonNull(membershipId);
        this.channelId = Objects.requireNonNull(channelId);
        this.userId = Objects.requireNonNull(userId);
        this.joinedAt = LocalDateTime.now();
    }

    public ChannelMembership(MembershipId membershipId, ChannelId channelId, 
                            UserId userId, LocalDateTime joinedAt) {
        this.membershipId = Objects.requireNonNull(membershipId);
        this.channelId = Objects.requireNonNull(channelId);
        this.userId = Objects.requireNonNull(userId);
        this.joinedAt = Objects.requireNonNull(joinedAt);
    }

    public MembershipId getMembershipId() { return membershipId; }
    public ChannelId getChannelId() { return channelId; }
    public UserId getUserId() { return userId; }
    public LocalDateTime getJoinedAt() { return joinedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChannelMembership that = (ChannelMembership) o;
        return Objects.equals(membershipId, that.membershipId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(membershipId);
    }
}
```

### 3.4 Message（メッセージ）

**責務**: チャンネル内のメッセージ管理

**属性**:
- `messageId`: メッセージID（識別子）
- `channelId`: チャンネルID
- `userId`: 送信者ID
- `content`: メッセージ本文
- `createdAt`: 送信日時

**ビジネスルール**:
- メッセージは1-2000文字
- 送信後の編集は不可（学習用として簡略化）
- 削除は論理削除（将来拡張）

**実装例**:

```java
package com.minislack.domain.model.message;

import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.user.UserId;
import java.time.LocalDateTime;
import java.util.Objects;

public class Message {
    private final MessageId messageId;
    private final ChannelId channelId;
    private final UserId userId;
    private final MessageContent content;
    private final LocalDateTime createdAt;

    public Message(MessageId messageId, ChannelId channelId, 
                   UserId userId, MessageContent content) {
        this.messageId = Objects.requireNonNull(messageId);
        this.channelId = Objects.requireNonNull(channelId);
        this.userId = Objects.requireNonNull(userId);
        this.content = Objects.requireNonNull(content);
        this.createdAt = LocalDateTime.now();
    }

    public Message(MessageId messageId, ChannelId channelId, 
                   UserId userId, MessageContent content, LocalDateTime createdAt) {
        this.messageId = Objects.requireNonNull(messageId);
        this.channelId = Objects.requireNonNull(channelId);
        this.userId = Objects.requireNonNull(userId);
        this.content = Objects.requireNonNull(content);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public MessageId getMessageId() { return messageId; }
    public ChannelId getChannelId() { return channelId; }
    public UserId getUserId() { return userId; }
    public MessageContent getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return Objects.equals(messageId, message.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId);
    }
}
```

---

## 4. 値オブジェクト設計

値オブジェクトは**不変（Immutable）**で、**等価性で比較**されます。

### 4.1 UserId

```java
package com.minislack.domain.model.user;

import java.util.Objects;
import java.util.UUID;

import org.springframework.lang.NonNull;

/**
 * ユーザーID値オブジェクト
 * UUIDv4を使用した一意識別子
 */
public class UserId {
    private final String value;

    private UserId(@NonNull String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("UserId must not be blank");
        }
        this.value = value;
    }

    @NonNull
    public static UserId of(@NonNull String value) {
        return new UserId(value);
    }

    @NonNull
    public static UserId newId() {
        return new UserId(UUID.randomUUID().toString());
    }

    @NonNull
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserId userId = (UserId) o;
        return Objects.equals(value, userId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    @NonNull
    public String toString() {
        return "UserId{" + value + '}';
    }
}
```

### 4.2 Username

```java
package com.minislack.domain.model.user;

import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.lang.NonNull;

/**
 * ユーザー名値オブジェクト
 * 3-20文字の英数字とアンダースコアのみ許可
 */
public class Username {
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
    private final String value;

    public Username(@NonNull String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Username must not be empty");
        }
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Username must be 3-20 characters and contain only alphanumeric and underscore"
            );
        }
        this.value = value;
    }

    @NonNull
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Username username = (Username) o;
        return Objects.equals(value, username.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    @NonNull
    public String toString() {
        return value;
    }
}
```

### 4.3 Email

```java
package com.minislack.domain.model.user;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.lang.NonNull;

/**
 * メールアドレス値オブジェクト
 * RFC準拠の簡易バリデーション
 */
public class Email {
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private final String value;

    public Email(@NonNull String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Email must not be empty");
        }
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        this.value = value.toLowerCase(Locale.ROOT);
    }

    @NonNull
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return Objects.equals(value, email.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    @NonNull
    public String toString() {
        return value;
    }
}
```

### 4.4 Password

```java
package com.minislack.domain.model.user;

import java.util.Objects;

import org.springframework.lang.NonNull;

/**
 * パスワード値オブジェクト
 * BCryptでハッシュ化して保存
 */
public class Password {
    private static final int MIN_LENGTH = 8;
    private final String hashedValue;

    // ハッシュ化済みパスワードから作成（リポジトリから復元時）
    private Password(@NonNull String hashedValue) {
        this.hashedValue = Objects.requireNonNull(hashedValue, "hashedValue must not be null");
    }

    // 生パスワードからハッシュ化して作成
    @NonNull
    public static Password fromRawPassword(@NonNull String rawPassword, @NonNull IPasswordEncoder encoder) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        Objects.requireNonNull(encoder, "encoder must not be null");
        
        if (rawPassword.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                "Password must be at least " + MIN_LENGTH + " characters"
            );
        }
        String hashed = encoder.encode(rawPassword);
        return new Password(hashed);
    }

    // ハッシュ化済みパスワードから復元
    @NonNull
    public static Password fromHashedValue(@NonNull String hashedValue) {
        return new Password(hashedValue);
    }

    // パスワード検証
    public boolean matches(@NonNull String rawPassword, @NonNull IPasswordEncoder encoder) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        Objects.requireNonNull(encoder, "encoder must not be null");
        return encoder.matches(rawPassword, this.hashedValue);
    }

    @NonNull
    public String getHashedValue() {
        return hashedValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Password password = (Password) o;
        return Objects.equals(hashedValue, password.hashedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hashedValue);
    }

    @Override
    @NonNull
    public String toString() {
        return "Password{***}"; // セキュリティのため内容は表示しない
    }
}
```

### 4.5 DisplayName

```java
package com.minislack.domain.model.user;

import java.util.Objects;

import org.springframework.lang.NonNull;

/**
 * 表示名値オブジェクト
 * 1-50文字
 */
public class DisplayName {
    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 50;
    private final String value;

    public DisplayName(@NonNull String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("DisplayName must not be empty");
        }
        String trimmed = value.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "DisplayName must be " + MIN_LENGTH + "-" + MAX_LENGTH + " characters"
            );
        }
        this.value = trimmed;
    }

    @NonNull
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DisplayName that = (DisplayName) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    @NonNull
    public String toString() {
        return value;
    }
}
```

### 4.6 その他の値オブジェクト

**ChannelId, ChannelName, Description, MembershipId, MessageId, MessageContent**も同様のパターンで実装します。

---

## 5. リポジトリインターフェース

### 5.1 IUserRepository

```java
package com.minislack.domain.model.user;

import java.util.Optional;
import java.util.List;

public interface IUserRepository {
    User save(User user);
    Optional<User> findById(UserId userId);
    Optional<User> findByEmail(Email email);
    Optional<User> findByUsername(Username username);
    boolean existsByEmail(Email email);
    boolean existsByUsername(Username username);
    List<User> findAll();
}
```

### 5.2 IChannelRepository

```java
package com.minislack.domain.model.channel;

import java.util.Optional;
import java.util.List;

public interface IChannelRepository {
    Channel save(Channel channel);
    Optional<Channel> findById(ChannelId channelId);
    Optional<Channel> findByName(ChannelName channelName);
    List<Channel> findAllPublic();
    List<Channel> findByMember(UserId userId);
    boolean existsByName(ChannelName channelName);
}
```

### 5.3 IChannelMembershipRepository

```java
package com.minislack.domain.model.channel;

import com.minislack.domain.model.user.UserId;
import java.util.Optional;
import java.util.List;

public interface IChannelMembershipRepository {
    ChannelMembership save(ChannelMembership membership);
    Optional<ChannelMembership> findById(MembershipId membershipId);
    Optional<ChannelMembership> findByChannelAndUser(ChannelId channelId, UserId userId);
    List<ChannelMembership> findByChannel(ChannelId channelId);
    List<ChannelMembership> findByUser(UserId userId);
    void delete(ChannelMembership membership);
    boolean existsByChannelAndUser(ChannelId channelId, UserId userId);
}
```

### 5.4 IMessageRepository

```java
package com.minislack.domain.model.message;

import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.user.UserId;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface IMessageRepository {
    Message save(Message message);
    Optional<Message> findById(MessageId messageId);
    List<Message> findByChannel(ChannelId channelId, int limit, int offset);
    List<Message> findByChannelAfter(ChannelId channelId, LocalDateTime after);
    List<Message> searchByKeyword(String keyword, UserId userId);
    long countByChannel(ChannelId channelId);
    
    // アーカイブ用
    List<Message> findOlderThan(LocalDateTime threshold);
    void deleteByIds(List<MessageId> messageIds);
}
```

---

## 6. ドメインサービス

エンティティや値オブジェクトに属さないビジネスロジックをドメインサービスとして定義します。

### 6.1 IPasswordEncoder

```java
package com.minislack.domain.service;

public interface IPasswordEncoder {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
```

### 6.2 ChannelMembershipService

```java
package com.minislack.domain.service;

import java.util.Objects;

import org.springframework.lang.NonNull;

import com.minislack.domain.model.channel.Channel;
import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.channel.ChannelMembership;
import com.minislack.domain.model.channel.IChannelMembershipRepository;
import com.minislack.domain.model.channel.IChannelRepository;
import com.minislack.domain.model.channel.MembershipId;
import com.minislack.domain.model.user.UserId;

/**
 * チャンネルメンバーシップに関するドメインサービス
 */
public class ChannelMembershipService {
    private final IChannelRepository channelRepository;
    private final IChannelMembershipRepository membershipRepository;

    public ChannelMembershipService(@NonNull IChannelRepository channelRepository, 
                                   @NonNull IChannelMembershipRepository membershipRepository) {
        this.channelRepository = Objects.requireNonNull(channelRepository);
        this.membershipRepository = Objects.requireNonNull(membershipRepository);
    }

    /**
     * ユーザーがチャンネルに参加可能か判定
     */
    public boolean canJoinChannel(@NonNull UserId userId, @NonNull ChannelId channelId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
        
        Channel channel = channelRepository.findById(channelId)
            .orElseThrow(() -> new IllegalArgumentException("Channel not found"));
        
        // 既に参加している場合は不可
        if (membershipRepository.existsByChannelAndUser(channelId, userId)) {
            return false;
        }
        
        // 公開チャンネルのみ参加可能
        return channel.canJoin();
    }

    /**
     * ユーザーをチャンネルに追加
     */
    @NonNull
    public ChannelMembership joinChannel(@NonNull UserId userId, @NonNull ChannelId channelId) {
        if (!canJoinChannel(userId, channelId)) {
            throw new IllegalStateException("Cannot join this channel");
        }
        
        ChannelMembership membership = new ChannelMembership(
            MembershipId.newId(),
            channelId,
            userId
        );
        
        return membershipRepository.save(membership);
    }
}
```

---

## 7. 集約（Aggregate）

集約は、整合性境界を持つエンティティの集まりです。

### 7.1 User集約

**集約ルート**: User
**含まれるオブジェクト**: UserId, Username, Email, Password, DisplayName

**整合性ルール**:
- ユーザー名とメールアドレスは一意
- パスワード変更時は現在のパスワード検証が必要

### 7.2 Channel集約

**集約ルート**: Channel
**含まれるオブジェクト**: ChannelId, ChannelName, Description

**整合性ルール**:
- チャンネル名は一意
- 作成者は自動的にメンバーになる

### 7.3 ChannelMembership集約

**集約ルート**: ChannelMembership
**含まれるオブジェクト**: MembershipId, ChannelId, UserId

**整合性ルール**:
- 同じユーザーが同じチャンネルに複数回参加できない

### 7.4 Message集約

**集約ルート**: Message
**含まれるオブジェクト**: MessageId, ChannelId, UserId, MessageContent

**整合性ルール**:
- メッセージは送信後変更不可

---

## 8. ドメインイベント

将来的な拡張として、ドメインイベントを定義します。

```java
package com.minislack.domain.event;

import com.minislack.domain.model.message.MessageId;
import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.user.UserId;
import java.time.LocalDateTime;

/**
 * メッセージ送信イベント
 */
public class MessageSentEvent {
    private final MessageId messageId;
    private final ChannelId channelId;
    private final UserId userId;
    private final String content;
    private final LocalDateTime occurredAt;

    public MessageSentEvent(MessageId messageId, ChannelId channelId, 
                           UserId userId, String content) {
        this.messageId = messageId;
        this.channelId = channelId;
        this.userId = userId;
        this.content = content;
        this.occurredAt = LocalDateTime.now();
    }

    // ゲッター省略
}
```

---

## 9. ドメインモデル図

```
┌─────────────┐         ┌──────────────┐
│    User     │         │   Channel    │
├─────────────┤         ├──────────────┤
│ - userId    │         │ - channelId  │
│ - username  │         │ - name       │
│ - email     │         │ - description│
│ - password  │         │ - isPublic   │
│ - displayNm │         │ - createdBy  │
└──────┬──────┘         └───────┬──────┘
       │                        │
       │   ┌────────────────────┘
       │   │
       │   │    ┌──────────────────────┐
       └───┼────│ ChannelMembership    │
           │    ├──────────────────────┤
           │    │ - membershipId       │
           │    │ - channelId (FK)     │
           │    │ - userId (FK)        │
           │    │ - joinedAt           │
           │    └──────────────────────┘
           │
           │    ┌──────────────────────┐
           └────│     Message          │
                ├──────────────────────┤
                │ - messageId          │
                │ - channelId (FK)     │
                │ - userId (FK)        │
                │ - content            │
                │ - createdAt          │
                └──────────────────────┘
```

---

## 10. まとめ

MiniSlackのドメインモデル設計のポイント：

1. **エンティティ**: User, Channel, ChannelMembership, Message
2. **値オブジェクト**: UserId, Username, Email, Password等（不変、バリデーション）
3. **リポジトリ**: データ永続化のインターフェース（ドメイン層で定義）
4. **ドメインサービス**: エンティティに属さないビジネスロジック
5. **集約**: 整合性境界を持つエンティティの集まり
6. **ドメインイベント**: ビジネス上重要な出来事（将来拡張）

次のステップでは、環境構築ガイドを作成し、実際にコードを書き始めます！

