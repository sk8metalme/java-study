# ドメイン層実装ハンズオン

## 1. はじめに

このドキュメントでは、MiniSlackのドメイン層を実際に実装していきます。

### 1.1 ドメイン層とは？

**ドメイン層**は、オニオンアーキテクチャの**最も内側**にあり、**ビジネスロジックの核心**を担当します。

**特徴**:
- フレームワーク非依存（可能な限りPure Java）
- ビジネスルールを表現
- 他のレイヤーに依存しない
- テストが容易

### 1.2 実装する内容

このハンズオンでは以下を実装します：

1. **値オブジェクト（Value Objects）** - 不変で検証ロジックを持つ
2. **エンティティ（Entities）** - 識別子を持ちビジネスロジックを実装
3. **リポジトリインターフェース** - データ永続化の契約
4. **ドメインサービス** - エンティティに属さないビジネスロジック

---

## 2. ディレクトリ構造の確認

まず、ドメイン層のディレクトリを確認します：

```text
src/main/java/com/minislack/domain/
├── model/
│   ├── user/           # ユーザー関連
│   ├── channel/        # チャンネル関連
│   └── message/        # メッセージ関連
├── service/            # ドメインサービス
└── exception/          # ドメイン例外
```

---

## 3. ユーザードメインの実装

### 3.1 UserId（値オブジェクト）

**ファイル**: `src/main/java/com/minislack/domain/model/user/UserId.java`

**目的**: ユーザーの一意識別子（UUIDv4）

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

**学習ポイント**:
- ✅ **不変性**: `final`フィールド、セッターなし
- ✅ **検証**: コンストラクタでバリデーション
- ✅ **ファクトリメソッド**: `of()`と`newId()`
- ✅ **等価性**: `equals()`と`hashCode()`の実装
- ✅ **Null安全性**: `@NonNull`アノテーション、`Objects.requireNonNull()`

**演習**:
1. このファイルを作成してください
2. IDEでコンパイルエラーがないか確認
3. 次のテストケースを考えてみましょう：
   - 空文字列でUserIdを作成したらどうなる？
   - nullでUserIdを作成したらどうなる？
   - 2つのUserIdが同じ値なら`equals()`は何を返す？

### 3.2 Username（値オブジェクト）

**ファイル**: `src/main/java/com/minislack/domain/model/user/Username.java`

**目的**: ユーザー名の検証とカプセル化

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

**学習ポイント**:
- ✅ **正規表現バリデーション**: `Pattern`で形式チェック
- ✅ **ビジネスルール**: 3-20文字、英数字とアンダースコアのみ
- ✅ **明確なエラーメッセージ**: 何が悪いのか具体的に

**演習**:
1. このファイルを作成
2. 以下の入力でどうなるか予想してみましょう：
   - `"ab"` → エラー（短すぎる）
   - `"abc123"` → OK
   - `"user@name"` → エラー（`@`は不許可）
   - `"very_long_username_exceeds_limit"` → エラー（長すぎる）

### 3.3 Email（値オブジェクト）

**ファイル**: `src/main/java/com/minislack/domain/model/user/Email.java`

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

**学習ポイント**:
- ✅ **正規化**: `toLowerCase(Locale.ROOT)`で小文字に統一
- ✅ **ロケール指定**: `Locale.ROOT`で予期しない大文字小文字変換を防ぐ

### 3.4 Password（値オブジェクト）

**ファイル**: `src/main/java/com/minislack/domain/model/user/Password.java`

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

    private Password(@NonNull String hashedValue) {
        this.hashedValue = Objects.requireNonNull(hashedValue, "hashedValue must not be null");
    }

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

    @NonNull
    public static Password fromHashedValue(@NonNull String hashedValue) {
        return new Password(hashedValue);
    }

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
        return "Password{***}";
    }
}
```

**学習ポイント**:
- ✅ **セキュリティ**: 生パスワードは保存せず、ハッシュ化
- ✅ **依存性逆転**: `IPasswordEncoder`はインターフェース（ドメイン層で定義）
- ✅ **ファクトリメソッド**: 用途別に`fromRawPassword()`と`fromHashedValue()`
- ✅ **プライベートコンストラクタ**: 外部から直接作成できない

**IPasswordEncoderインターフェース**:

**ファイル**: `src/main/java/com/minislack/domain/model/user/IPasswordEncoder.java`

```java
package com.minislack.domain.model.user;

import org.springframework.lang.NonNull;

/**
 * パスワードエンコーダーインターフェース
 * ドメイン層で定義、インフラ層で実装
 */
public interface IPasswordEncoder {
    @NonNull
    String encode(@NonNull String rawPassword);
    
    boolean matches(@NonNull String rawPassword, @NonNull String encodedPassword);
}
```

### 3.5 DisplayName（値オブジェクト）

**ファイル**: `src/main/java/com/minislack/domain/model/user/DisplayName.java`

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

**学習ポイント**:
- ✅ **トリミング**: 前後の空白を自動削除
- ✅ **長さ制限**: 定数で管理

---

## 4. Userエンティティの実装

### 4.1 Userエンティティ

**ファイル**: `src/main/java/com/minislack/domain/model/user/User.java`

```java
package com.minislack.domain.model.user;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.lang.NonNull;

/**
 * ユーザーエンティティ
 * ドメインの中核概念。ビジネスルールを持つ。
 */
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
    public void changePassword(@NonNull Password currentPassword, @NonNull Password newPassword, 
                              @NonNull IPasswordEncoder encoder) {
        Objects.requireNonNull(currentPassword, "currentPassword must not be null");
        Objects.requireNonNull(newPassword, "newPassword must not be null");
        Objects.requireNonNull(encoder, "encoder must not be null");
        
        if (!this.password.matches(currentPassword.getHashedValue(), encoder)) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        this.password = newPassword;
        this.updatedAt = LocalDateTime.now();
    }

    // ビジネスロジック: プロフィール更新
    public void updateProfile(@NonNull DisplayName newDisplayName) {
        Objects.requireNonNull(newDisplayName, "newDisplayName must not be null");
        this.displayName = newDisplayName;
        this.updatedAt = LocalDateTime.now();
    }

    // ゲッター
    @NonNull
    public UserId getUserId() { return userId; }
    
    @NonNull
    public Username getUsername() { return username; }
    
    @NonNull
    public Email getEmail() { return email; }
    
    @NonNull
    public Password getPassword() { return password; }
    
    @NonNull
    public DisplayName getDisplayName() { return displayName; }
    
    @NonNull
    public LocalDateTime getCreatedAt() { return createdAt; }
    
    @NonNull
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

    @Override
    @NonNull
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", username=" + username +
                ", email=" + email +
                ", displayName=" + displayName +
                '}';
    }
}
```

**学習ポイント**:
- ✅ **2つのコンストラクタ**: 新規作成用と復元用
- ✅ **ビジネスロジック**: `changePassword()`、`updateProfile()`
- ✅ **識別子で等価性判定**: `equals()`は`userId`のみで比較
- ✅ **不変フィールド**: `userId`と`createdAt`は`final`

**演習**:
1. なぜ`userId`と`createdAt`は`final`なのか？
2. なぜ`equals()`は`userId`だけで比較するのか？
3. `toString()`でパスワードを含めないのはなぜ？

### 4.2 IUserRepository（リポジトリインターフェース）

**ファイル**: `src/main/java/com/minislack/domain/model/user/IUserRepository.java`

```java
package com.minislack.domain.model.user;

import java.util.List;
import java.util.Optional;

import org.springframework.lang.NonNull;

/**
 * ユーザーリポジトリインターフェース
 * ドメイン層で定義し、インフラ層で実装する（依存性逆転）
 */
public interface IUserRepository {
    @NonNull
    User save(@NonNull User user);
    
    @NonNull
    Optional<User> findById(@NonNull UserId userId);
    
    @NonNull
    Optional<User> findByEmail(@NonNull Email email);
    
    @NonNull
    Optional<User> findByUsername(@NonNull Username username);
    
    boolean existsByEmail(@NonNull Email email);
    
    boolean existsByUsername(@NonNull Username username);
    
    @NonNull
    List<User> findAll();
}
```

**学習ポイント**:
- ✅ **インターフェース**: 実装の詳細は隠蔽
- ✅ **Optional**: 見つからない可能性があるメソッドは`Optional<>`を返す
- ✅ **依存性逆転の原則（DIP）**: ドメイン層で定義、インフラ層で実装

**演習**:
1. なぜ`findById()`は`User`ではなく`Optional<User>`を返すのか？
2. `existsByEmail()`と`findByEmail()`の使い分けは？

---

## 5. チャンネルドメインの実装

### 5.1 ChannelId（値オブジェクト）

**ファイル**: `src/main/java/com/minislack/domain/model/channel/ChannelId.java`

```java
package com.minislack.domain.model.channel;

import java.util.Objects;
import java.util.UUID;

import org.springframework.lang.NonNull;

/**
 * チャンネルID値オブジェクト
 */
public class ChannelId {
    private final String value;

    private ChannelId(@NonNull String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ChannelId must not be blank");
        }
        this.value = value;
    }

    @NonNull
    public static ChannelId of(@NonNull String value) {
        return new ChannelId(value);
    }

    @NonNull
    public static ChannelId newId() {
        return new ChannelId(UUID.randomUUID().toString());
    }

    @NonNull
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChannelId channelId = (ChannelId) o;
        return Objects.equals(value, channelId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    @NonNull
    public String toString() {
        return "ChannelId{" + value + '}';
    }
}
```

### 5.2 ChannelName（値オブジェクト）

**ファイル**: `src/main/java/com/minislack/domain/model/channel/ChannelName.java`

```java
package com.minislack.domain.model.channel;

import java.util.Objects;

import org.springframework.lang.NonNull;

/**
 * チャンネル名値オブジェクト
 * 4-50文字
 */
public class ChannelName {
    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 50;
    private final String value;

    public ChannelName(@NonNull String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ChannelName must not be empty");
        }
        String trimmed = value.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "ChannelName must be " + MIN_LENGTH + "-" + MAX_LENGTH + " characters"
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
        ChannelName that = (ChannelName) o;
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

### 5.3 Description（値オブジェクト）

**ファイル**: `src/main/java/com/minislack/domain/model/channel/Description.java`

```java
package com.minislack.domain.model.channel;

import java.util.Objects;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * チャンネル説明値オブジェクト
 * 0-500文字（オプショナル）
 */
public class Description {
    private static final int MAX_LENGTH = 500;
    private final String value;

    public Description(@Nullable String value) {
        if (value == null || value.isBlank()) {
            this.value = "";
        } else {
            String trimmed = value.trim();
            if (trimmed.length() > MAX_LENGTH) {
                throw new IllegalArgumentException(
                    "Description must be " + MAX_LENGTH + " characters or less"
                );
            }
            this.value = trimmed;
        }
    }

    @NonNull
    public String getValue() {
        return value;
    }

    public boolean isEmpty() {
        return value.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Description that = (Description) o;
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

**学習ポイント**:
- ✅ **Nullable許可**: 説明は任意項目なので`@Nullable`
- ✅ **デフォルト値**: nullや空白は空文字列に変換

### 5.4 Channelエンティティ

**ファイル**: `src/main/java/com/minislack/domain/model/channel/Channel.java`

```java
package com.minislack.domain.model.channel;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.lang.NonNull;

import com.minislack.domain.model.user.UserId;

/**
 * チャンネルエンティティ
 */
public class Channel {
    private final ChannelId channelId;
    private ChannelName channelName;
    private Description description;
    private final boolean isPublic;
    private final UserId createdBy;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 新規チャンネル作成用
    public Channel(@NonNull ChannelId channelId, @NonNull ChannelName channelName, 
                   @NonNull Description description, boolean isPublic, @NonNull UserId createdBy) {
        this.channelId = Objects.requireNonNull(channelId, "channelId must not be null");
        this.channelName = Objects.requireNonNull(channelName, "channelName must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.isPublic = isPublic;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 既存チャンネル復元用
    public Channel(@NonNull ChannelId channelId, @NonNull ChannelName channelName, 
                   @NonNull Description description, boolean isPublic, @NonNull UserId createdBy,
                   @NonNull LocalDateTime createdAt, @NonNull LocalDateTime updatedAt) {
        this.channelId = Objects.requireNonNull(channelId, "channelId must not be null");
        this.channelName = Objects.requireNonNull(channelName, "channelName must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
        this.isPublic = isPublic;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    // ビジネスロジック: チャンネル情報更新
    public void updateInfo(@NonNull ChannelName newName, @NonNull Description newDescription) {
        Objects.requireNonNull(newName, "newName must not be null");
        Objects.requireNonNull(newDescription, "newDescription must not be null");
        this.channelName = newName;
        this.description = newDescription;
        this.updatedAt = LocalDateTime.now();
    }

    // ビジネスロジック: 参加可能か判定
    public boolean canJoin() {
        return this.isPublic;
    }

    // ゲッター
    @NonNull
    public ChannelId getChannelId() { return channelId; }
    
    @NonNull
    public ChannelName getChannelName() { return channelName; }
    
    @NonNull
    public Description getDescription() { return description; }
    
    public boolean isPublic() { return isPublic; }
    
    @NonNull
    public UserId getCreatedBy() { return createdBy; }
    
    @NonNull
    public LocalDateTime getCreatedAt() { return createdAt; }
    
    @NonNull
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

    @Override
    @NonNull
    public String toString() {
        return "Channel{" +
                "channelId=" + channelId +
                ", channelName=" + channelName +
                ", isPublic=" + isPublic +
                '}';
    }
}
```

**学習ポイント**:
- ✅ **ビジネスロジック**: `canJoin()`で参加可否を判定
- ✅ **不変フィールド**: `isPublic`は作成後変更不可

### 5.5 IChannelRepository

**ファイル**: `src/main/java/com/minislack/domain/model/channel/IChannelRepository.java`

```java
package com.minislack.domain.model.channel;

import java.util.List;
import java.util.Optional;

import org.springframework.lang.NonNull;

import com.minislack.domain.model.user.UserId;

/**
 * チャンネルリポジトリインターフェース
 */
public interface IChannelRepository {
    @NonNull
    Channel save(@NonNull Channel channel);
    
    @NonNull
    Optional<Channel> findById(@NonNull ChannelId channelId);
    
    @NonNull
    Optional<Channel> findByName(@NonNull ChannelName channelName);
    
    @NonNull
    List<Channel> findAllPublic();
    
    @NonNull
    List<Channel> findByMember(@NonNull UserId userId);
    
    boolean existsByName(@NonNull ChannelName channelName);
}
```

---

## 6. チャンネルメンバーシップの実装

### 6.1 MembershipId（値オブジェクト）

**ファイル**: `src/main/java/com/minislack/domain/model/channel/MembershipId.java`

```java
package com.minislack.domain.model.channel;

import java.util.Objects;
import java.util.UUID;

import org.springframework.lang.NonNull;

/**
 * メンバーシップID値オブジェクト
 */
public class MembershipId {
    private final String value;

    private MembershipId(@NonNull String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("MembershipId must not be blank");
        }
        this.value = value;
    }

    @NonNull
    public static MembershipId of(@NonNull String value) {
        return new MembershipId(value);
    }

    @NonNull
    public static MembershipId newId() {
        return new MembershipId(UUID.randomUUID().toString());
    }

    @NonNull
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MembershipId that = (MembershipId) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    @NonNull
    public String toString() {
        return "MembershipId{" + value + '}';
    }
}
```

### 6.2 ChannelMembershipエンティティ

**ファイル**: `src/main/java/com/minislack/domain/model/channel/ChannelMembership.java`

```java
package com.minislack.domain.model.channel;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.lang.NonNull;

import com.minislack.domain.model.user.UserId;

/**
 * チャンネルメンバーシップエンティティ
 * ユーザーとチャンネルの関連を表現
 */
public class ChannelMembership {
    private final MembershipId membershipId;
    private final ChannelId channelId;
    private final UserId userId;
    private final LocalDateTime joinedAt;

    public ChannelMembership(@NonNull MembershipId membershipId, @NonNull ChannelId channelId, 
                            @NonNull UserId userId) {
        this.membershipId = Objects.requireNonNull(membershipId, "membershipId must not be null");
        this.channelId = Objects.requireNonNull(channelId, "channelId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.joinedAt = LocalDateTime.now();
    }

    public ChannelMembership(@NonNull MembershipId membershipId, @NonNull ChannelId channelId, 
                            @NonNull UserId userId, @NonNull LocalDateTime joinedAt) {
        this.membershipId = Objects.requireNonNull(membershipId, "membershipId must not be null");
        this.channelId = Objects.requireNonNull(channelId, "channelId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt must not be null");
    }

    @NonNull
    public MembershipId getMembershipId() { return membershipId; }
    
    @NonNull
    public ChannelId getChannelId() { return channelId; }
    
    @NonNull
    public UserId getUserId() { return userId; }
    
    @NonNull
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

    @Override
    @NonNull
    public String toString() {
        return "ChannelMembership{" +
                "membershipId=" + membershipId +
                ", channelId=" + channelId +
                ", userId=" + userId +
                ", joinedAt=" + joinedAt +
                '}';
    }
}
```

**学習ポイント**:
- ✅ **全フィールドfinal**: メンバーシップは作成後変更不可
- ✅ **関連の表現**: UserとChannelの多対多関係を表現

### 6.3 IChannelMembershipRepository

**ファイル**: `src/main/java/com/minislack/domain/model/channel/IChannelMembershipRepository.java`

```java
package com.minislack.domain.model.channel;

import java.util.List;
import java.util.Optional;

import org.springframework.lang.NonNull;

import com.minislack.domain.model.user.UserId;

/**
 * チャンネルメンバーシップリポジトリインターフェース
 */
public interface IChannelMembershipRepository {
    @NonNull
    ChannelMembership save(@NonNull ChannelMembership membership);
    
    @NonNull
    Optional<ChannelMembership> findById(@NonNull MembershipId membershipId);
    
    @NonNull
    Optional<ChannelMembership> findByChannelAndUser(@NonNull ChannelId channelId, @NonNull UserId userId);
    
    @NonNull
    List<ChannelMembership> findByChannel(@NonNull ChannelId channelId);
    
    @NonNull
    List<ChannelMembership> findByUser(@NonNull UserId userId);
    
    void delete(@NonNull ChannelMembership membership);
    
    boolean existsByChannelAndUser(@NonNull ChannelId channelId, @NonNull UserId userId);
}
```

---

## 7. メッセージドメインの実装

### 7.1 MessageId、MessageContent（値オブジェクト）

**ファイル**: `src/main/java/com/minislack/domain/model/message/MessageId.java`

```java
package com.minislack.domain.model.message;

import java.util.Objects;
import java.util.UUID;

import org.springframework.lang.NonNull;

/**
 * メッセージID値オブジェクト
 */
public class MessageId {
    private final String value;

    private MessageId(@NonNull String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("MessageId must not be blank");
        }
        this.value = value;
    }

    @NonNull
    public static MessageId of(@NonNull String value) {
        return new MessageId(value);
    }

    @NonNull
    public static MessageId newId() {
        return new MessageId(UUID.randomUUID().toString());
    }

    @NonNull
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageId messageId = (MessageId) o;
        return Objects.equals(value, messageId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    @NonNull
    public String toString() {
        return "MessageId{" + value + '}';
    }
}
```

**ファイル**: `src/main/java/com/minislack/domain/model/message/MessageContent.java`

```java
package com.minislack.domain.model.message;

import java.util.Objects;

import org.springframework.lang.NonNull;

/**
 * メッセージ本文値オブジェクト
 * 1-2000文字
 */
public class MessageContent {
    private static final int MIN_LENGTH = 1;
    private static final int MAX_LENGTH = 2000;
    private final String value;

    public MessageContent(@NonNull String value) {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("MessageContent must not be empty");
        }
        String trimmed = value.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "MessageContent must be " + MIN_LENGTH + "-" + MAX_LENGTH + " characters"
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
        MessageContent that = (MessageContent) o;
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

### 7.2 Messageエンティティ

**ファイル**: `src/main/java/com/minislack/domain/model/message/Message.java`

```java
package com.minislack.domain.model.message;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.lang.NonNull;

import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.user.UserId;

/**
 * メッセージエンティティ
 */
public class Message {
    private final MessageId messageId;
    private final ChannelId channelId;
    private final UserId userId;
    private final MessageContent content;
    private final LocalDateTime createdAt;

    public Message(@NonNull MessageId messageId, @NonNull ChannelId channelId, 
                   @NonNull UserId userId, @NonNull MessageContent content) {
        this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
        this.channelId = Objects.requireNonNull(channelId, "channelId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.createdAt = LocalDateTime.now();
    }

    public Message(@NonNull MessageId messageId, @NonNull ChannelId channelId, 
                   @NonNull UserId userId, @NonNull MessageContent content, 
                   @NonNull LocalDateTime createdAt) {
        this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
        this.channelId = Objects.requireNonNull(channelId, "channelId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    @NonNull
    public MessageId getMessageId() { return messageId; }
    
    @NonNull
    public ChannelId getChannelId() { return channelId; }
    
    @NonNull
    public UserId getUserId() { return userId; }
    
    @NonNull
    public MessageContent getContent() { return content; }
    
    @NonNull
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

    @Override
    @NonNull
    public String toString() {
        return "Message{" +
                "messageId=" + messageId +
                ", channelId=" + channelId +
                ", userId=" + userId +
                ", createdAt=" + createdAt +
                '}';
    }
}
```

**学習ポイント**:
- ✅ **イミュータブル**: 全フィールドが`final`（メッセージは送信後変更不可）
- ✅ **シンプル**: ビジネスロジックなし（単純なデータ保持）

### 7.3 IMessageRepository

**ファイル**: `src/main/java/com/minislack/domain/model/message/IMessageRepository.java`

```java
package com.minislack.domain.model.message;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.lang.NonNull;

import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.user.UserId;

/**
 * メッセージリポジトリインターフェース
 */
public interface IMessageRepository {
    @NonNull
    Message save(@NonNull Message message);
    
    @NonNull
    Optional<Message> findById(@NonNull MessageId messageId);
    
    @NonNull
    List<Message> findByChannel(@NonNull ChannelId channelId, int limit, int offset);
    
    @NonNull
    List<Message> findByChannelAfter(@NonNull ChannelId channelId, @NonNull LocalDateTime after);
    
    @NonNull
    List<Message> searchByKeyword(@NonNull String keyword, @NonNull UserId userId);
    
    long countByChannel(@NonNull ChannelId channelId);
    
    // アーカイブ用
    @NonNull
    List<Message> findOlderThan(@NonNull LocalDateTime threshold);
    
    void deleteByIds(@NonNull List<MessageId> messageIds);
}
```

---

## 8. ドメインサービスの実装

### 8.1 ChannelMembershipService

**ファイル**: `src/main/java/com/minislack/domain/service/ChannelMembershipService.java`

```java
package com.minislack.domain.service;

import java.util.Objects;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.minislack.domain.exception.ResourceNotFoundException;
import com.minislack.domain.model.channel.Channel;
import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.channel.ChannelMembership;
import com.minislack.domain.model.channel.IChannelMembershipRepository;
import com.minislack.domain.model.channel.IChannelRepository;
import com.minislack.domain.model.channel.MembershipId;
import com.minislack.domain.model.user.UserId;

/**
 * チャンネルメンバーシップに関するドメインサービス
 * エンティティに属さないビジネスロジック
 */
@Component
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
            .orElseThrow(() -> new ResourceNotFoundException("Channel", channelId.getValue()));
        
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

**学習ポイント**:
- ✅ **ドメインサービス**: 複数のエンティティにまたがるロジック
- ✅ **@Component**: ドメインサービスは例外的にSpringアノテーションを使用可能
- ✅ **ビジネスルール**: 公開チャンネルのみ参加可能、重複参加不可

---

## 9. ドメイン例外の実装

### 9.1 基底例外クラス

**ファイル**: `src/main/java/com/minislack/domain/exception/DomainException.java`

```java
package com.minislack.domain.exception;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * ドメイン層の基底例外
 */
public class DomainException extends RuntimeException {
    
    public DomainException(@NonNull String message) {
        super(message);
    }
    
    public DomainException(@NonNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
```

### 9.2 具体的な例外クラス

**ファイル**: `src/main/java/com/minislack/domain/exception/ResourceNotFoundException.java`

```java
package com.minislack.domain.exception;

import org.springframework.lang.NonNull;

/**
 * リソース未検出例外
 */
public class ResourceNotFoundException extends DomainException {
    private final String resourceType;
    private final String resourceId;
    
    public ResourceNotFoundException(@NonNull String resourceType, @NonNull String resourceId) {
        super(String.format("%s with id '%s' not found", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    @NonNull
    public String getResourceType() { return resourceType; }
    
    @NonNull
    public String getResourceId() { return resourceId; }
}
```

**ファイル**: `src/main/java/com/minislack/domain/exception/DuplicateResourceException.java`

```java
package com.minislack.domain.exception;

import org.springframework.lang.NonNull;

/**
 * 重複リソース例外
 */
public class DuplicateResourceException extends DomainException {
    public DuplicateResourceException(@NonNull String message) {
        super(message);
    }
}
```

**ファイル**: `src/main/java/com/minislack/domain/exception/BusinessRuleViolationException.java`

```java
package com.minislack.domain.exception;

import org.springframework.lang.NonNull;

/**
 * ビジネスルール違反例外
 */
public class BusinessRuleViolationException extends DomainException {
    public BusinessRuleViolationException(@NonNull String message) {
        super(message);
    }
}
```

---

## 10. 実装の確認

### 10.1 ファイル作成チェックリスト

以下のファイルを作成したか確認しましょう：

**ユーザードメイン**:
- [ ] `UserId.java`
- [ ] `Username.java`
- [ ] `Email.java`
- [ ] `Password.java`
- [ ] `DisplayName.java`
- [ ] `IPasswordEncoder.java`
- [ ] `User.java`
- [ ] `IUserRepository.java`

**チャンネルドメイン**:
- [ ] `ChannelId.java`
- [ ] `ChannelName.java`
- [ ] `Description.java`
- [ ] `Channel.java`
- [ ] `IChannelRepository.java`
- [ ] `MembershipId.java`
- [ ] `ChannelMembership.java`
- [ ] `IChannelMembershipRepository.java`

**メッセージドメイン**:
- [ ] `MessageId.java`
- [ ] `MessageContent.java`
- [ ] `Message.java`
- [ ] `IMessageRepository.java`

**ドメインサービス**:
- [ ] `ChannelMembershipService.java`

**ドメイン例外**:
- [ ] `DomainException.java`
- [ ] `ResourceNotFoundException.java`
- [ ] `DuplicateResourceException.java`
- [ ] `BusinessRuleViolationException.java`

### 10.2 コンパイル確認

```bash
# Gradleでコンパイル
./gradlew compileJava

# エラーがなければ成功
```

---

## 11. 単体テストの作成

ドメイン層はフレームワーク非依存なので、シンプルな単体テストが書けます。

### 11.1 Username のテスト

**ファイル**: `src/test/java/com/minislack/domain/model/user/UsernameTest.java`

```java
package com.minislack.domain.model.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UsernameTest {

    @Test
    void constructor_ValidUsername_Success() {
        // Given
        String validUsername = "test_user123";
        
        // When
        Username username = new Username(validUsername);
        
        // Then
        assertEquals(validUsername, username.getValue());
    }

    @Test
    void constructor_TooShort_ThrowsException() {
        // Given
        String shortUsername = "ab";
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            new Username(shortUsername);
        });
    }

    @Test
    void constructor_TooLong_ThrowsException() {
        // Given
        String longUsername = "this_is_a_very_long_username";
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            new Username(longUsername);
        });
    }

    @Test
    void constructor_InvalidCharacters_ThrowsException() {
        // Given
        String invalidUsername = "user@name";
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            new Username(invalidUsername);
        });
    }

    @Test
    void equals_SameValue_ReturnsTrue() {
        // Given
        Username username1 = new Username("testuser");
        Username username2 = new Username("testuser");
        
        // When & Then
        assertEquals(username1, username2);
    }
}
```

**演習**:
同様のテストを以下のクラスにも作成してみましょう：
- `EmailTest.java`
- `DisplayNameTest.java`
- `UserIdTest.java`

---

## 12. まとめ

### 12.1 ドメイン層実装の要点

1. **値オブジェクト**:
   - 不変（`final`フィールド）
   - バリデーションロジックを持つ
   - `equals()`と`hashCode()`を実装

2. **エンティティ**:
   - 識別子（ID）を持つ
   - ビジネスロジックを実装
   - 識別子で等価性を判定

3. **リポジトリインターフェース**:
   - ドメイン層で定義
   - インフラ層で実装（依存性逆転）

4. **ドメインサービス**:
   - 複数のエンティティにまたがるロジック
   - エンティティに属さないビジネスルール

### 12.2 次のステップ

ドメイン層の実装が完了しました！次はアプリケーション層を実装します：

- [07-application-layer.md](07-application-layer.md) - アプリケーション層実装

---

## 13. よくある質問

### Q1. なぜ値オブジェクトにセッターがないのか？

**A**: 不変性を保つためです。値オブジェクトは作成後に変更できません。変更が必要な場合は、新しいインスタンスを作成します。

### Q2. `@NonNull`アノテーションは本当に必要？

**A**: 必須ではありませんが、以下のメリットがあります：
- NullAwayなどの静的解析ツールが活用できる
- IDEが警告を出してくれる
- ドキュメントとしての役割

### Q3. ドメイン層でSpringのアノテーションを使って良いのか？

**A**: 原則はPure Javaですが、以下は許容されます：
- `@NonNull/@Nullable`: Null安全性のため
- `@Component`: ドメインサービスのみ（DIコンテナで管理するため）

避けるべき：
- `@Entity`, `@Table`: JPAはインフラ層の関心事
- `@Transactional`: アプリケーション層の関心事

### Q4. なぜエンティティに2つのコンストラクタがあるのか？

**A**: 
- **新規作成用**: `createdAt`と`updatedAt`を自動設定
- **復元用**: データベースから取得したデータで復元

---

## 14. 参考資料

- [Value Objects - Domain-Driven Design](https://martinfowler.com/bliki/ValueObject.html)
- [Entities - Eric Evans](https://www.domainlanguage.com/ddd/)
- [Java Records](https://openjdk.org/jeps/395) - Java 14以降の値オブジェクト簡略化

