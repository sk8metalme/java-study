# アプリケーション層実装ハンズオン

## 1. はじめに

このドキュメントでは、MiniSlackのアプリケーション層を実装していきます。

### 1.1 アプリケーション層とは？

**アプリケーション層**は、**ユースケース（利用シナリオ）を実装**するレイヤーです。

**責務**:
- ユースケースの実行（オーケストレーション）
- トランザクション境界の管理
- ドメインオブジェクトの協調

**特徴**:
- ドメイン層に依存
- ビジネスロジックは持たない（ドメイン層に委譲）
- Spring等のフレームワークを使用可能

---

## 2. ディレクトリ構造

```text
src/main/java/com/minislack/application/
├── user/              # ユーザー関連ユースケース
├── channel/           # チャンネル関連ユースケース
├── message/           # メッセージ関連ユースケース
└── exception/         # アプリケーション層の例外
```

---

## 3. コマンドオブジェクトの実装

### 3.1 コマンドオブジェクトとは？

**コマンドオブジェクト**は、ユースケースへの入力データを表現するオブジェクトです。

**特徴**:
- イミュータブル
- バリデーション不要（ドメイン層で検証）
- DTOとドメインの橋渡し

### 3.2 RegisterUserCommand

**ファイル**: `src/main/java/com/minislack/application/user/RegisterUserCommand.java`

```java
package com.minislack.application.user;

import org.springframework.lang.NonNull;

/**
 * ユーザー登録コマンド
 */
public class RegisterUserCommand {
    private final String username;
    private final String email;
    private final String password;
    private final String displayName;

    public RegisterUserCommand(@NonNull String username, @NonNull String email, 
                               @NonNull String password, @NonNull String displayName) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    @NonNull
    public String getUsername() { return username; }
    
    @NonNull
    public String getEmail() { return email; }
    
    @NonNull
    public String getPassword() { return password; }
    
    @NonNull
    public String getDisplayName() { return displayName; }
}
```

**学習ポイント**:
- ✅ **イミュータブル**: `final`フィールド、セッターなし
- ✅ **シンプル**: バリデーションロジックなし（値オブジェクトで検証）

---

## 4. ユーザー管理ユースケースの実装

### 4.1 UserRegistrationService

**ファイル**: `src/main/java/com/minislack/application/user/UserRegistrationService.java`

```java
package com.minislack.application.user;

import java.util.Objects;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minislack.domain.exception.DuplicateResourceException;
import com.minislack.domain.model.user.DisplayName;
import com.minislack.domain.model.user.Email;
import com.minislack.domain.model.user.IPasswordEncoder;
import com.minislack.domain.model.user.IUserRepository;
import com.minislack.domain.model.user.Password;
import com.minislack.domain.model.user.User;
import com.minislack.domain.model.user.UserId;
import com.minislack.domain.model.user.Username;

/**
 * ユーザー登録アプリケーションサービス
 * ユースケース「ユーザー登録」を実装
 */
@Service
public class UserRegistrationService {
    private final IUserRepository userRepository;
    private final IPasswordEncoder passwordEncoder;

    public UserRegistrationService(@NonNull IUserRepository userRepository, 
                                   @NonNull IPasswordEncoder passwordEncoder) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
    }

    @Transactional
    @NonNull
    public UserId registerUser(@NonNull RegisterUserCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        
        // 1. 値オブジェクトの作成（バリデーション）
        Username username = new Username(command.getUsername());
        Email email = new Email(command.getEmail());
        DisplayName displayName = new DisplayName(command.getDisplayName());

        // 2. 重複チェック（ビジネスルール）
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email already exists: " + email.getValue());
        }
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username already exists: " + username.getValue());
        }

        // 3. パスワードのハッシュ化
        Password password = Password.fromRawPassword(command.getPassword(), passwordEncoder);

        // 4. エンティティの作成
        User user = new User(UserId.newId(), username, email, password, displayName);

        // 5. 永続化
        User savedUser = userRepository.save(user);

        return savedUser.getUserId();
    }
}
```

**学習ポイント**:
- ✅ **@Service**: Springのサービスコンポーネント
- ✅ **@Transactional**: トランザクション境界（ユースケース単位）
- ✅ **コンストラクタインジェクション**: 依存性注入
- ✅ **オーケストレーション**: ドメインオブジェクトを協調させる
- ✅ **ビジネスロジックなし**: ドメイン層に委譲

### トランザクション管理をアプリケーション層で行う理由

**Q: なぜアプリケーション層でトランザクションを管理するのか？**

**A: ユースケース（ビジネストランザクション）の境界と一致するから**

#### レイヤー別の比較

| レイヤー | トランザクション管理 | 理由 |
|---------|-------------------|------|
| **ドメイン層** | ❌ 不適切 | フレームワーク非依存を保つため。`@Transactional`はSpring依存 |
| **アプリケーション層** | ✅ **最適** | ユースケース単位で一貫性を保証。複数リポジトリ操作をまとめる |
| **インフラ層** | △ 場合による | 個別のDB操作には適しているが、ビジネストランザクションには不適切 |
| **プレゼンテーション層** | ❌ 不適切 | HTTPリクエスト単位になり、ビジネスロジックと無関係な境界 |

#### 具体例で理解する

**ユーザー登録のユースケース**:
```
1. 重複チェック（SELECT）
2. ユーザー作成（INSERT）
3. 初期設定の作成（INSERT）
```

これらは**1つのビジネストランザクション**です。

**❌ ドメイン層でトランザクション管理した場合**:
```java
// ドメイン層
public class User {
    @Transactional  // ❌ Springに依存してしまう
    public void save() {
        // ...
    }
}
```
問題点：
- ドメイン層がフレームワークに依存
- テストが困難（Springコンテキストが必要）
- ドメインの純粋性が失われる

**❌ インフラ層でトランザクション管理した場合**:
```java
// インフラ層
@Repository
public class UserRepositoryImpl {
    @Transactional  // ❌ 個別操作のトランザクション
    public User save(User user) {
        // ...
    }
}
```
問題点：
- 複数のリポジトリ操作が別々のトランザクションになる
- ユーザー作成と初期設定作成の間で整合性が保証されない
- ロールバック範囲が狭すぎる

**✅ アプリケーション層でトランザクション管理（推奨）**:
```java
// アプリケーション層
@Service
public class UserRegistrationService {
    @Transactional  // ✅ ユースケース全体を1トランザクションに
    public UserId registerUser(RegisterUserCommand command) {
        // 1. 重複チェック（SELECT）
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("...");
        }
        
        // 2. ユーザー作成（INSERT）
        User user = new User(...);
        userRepository.save(user);
        
        // 3. 初期設定作成（INSERT）
        UserSettings settings = new UserSettings(...);
        settingsRepository.save(settings);
        
        // すべて成功 → コミット
        // 途中で例外 → 全体ロールバック
        return user.getUserId();
    }
}
```

メリット：
- ✅ ユースケース全体の一貫性保証
- ✅ ドメイン層はフレームワーク非依存を維持
- ✅ 複数リポジトリ操作を1トランザクションにまとめられる
- ✅ ロールバック範囲が適切

#### まとめ

**トランザクション境界 = ユースケース境界**

- アプリケーション層はユースケースを実装する場所
- よって、トランザクション管理もアプリケーション層で行う
- これがオニオンアーキテクチャの原則と一致する

**処理フロー**:
```text
1. コマンド受け取り
2. 値オブジェクト作成（バリデーション）
3. ビジネスルール検証（重複チェック）
4. エンティティ作成
5. リポジトリで永続化
6. 結果を返却
```

### 4.2 UserAuthenticationService

**ファイル**: `src/main/java/com/minislack/application/user/UserAuthenticationService.java`

```java
package com.minislack.application.user;

import java.util.Objects;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minislack.application.exception.AuthenticationException;
import com.minislack.domain.model.user.Email;
import com.minislack.domain.model.user.IPasswordEncoder;
import com.minislack.domain.model.user.IUserRepository;
import com.minislack.domain.model.user.User;

/**
 * ユーザー認証アプリケーションサービス
 * ユースケース「ユーザーログイン」を実装
 */
@Service
public class UserAuthenticationService {
    private final IUserRepository userRepository;
    private final IPasswordEncoder passwordEncoder;

    public UserAuthenticationService(@NonNull IUserRepository userRepository,
                                     @NonNull IPasswordEncoder passwordEncoder) {
        this.userRepository = Objects.requireNonNull(userRepository);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
    }

    @Transactional(readOnly = true)
    @NonNull
    public User authenticate(@NonNull String emailOrUsername, @NonNull String rawPassword) {
        Objects.requireNonNull(emailOrUsername, "emailOrUsername must not be null");
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        
        // メールアドレスで検索
        User user = null;
        try {
            Email email = new Email(emailOrUsername);
            user = userRepository.findByEmail(email).orElse(null);
        } catch (IllegalArgumentException e) {
            // メールアドレス形式でない場合、ユーザー名として扱う
        }
        
        // ユーザー名で検索（メールで見つからなかった場合）
        if (user == null) {
            throw new AuthenticationException("Invalid credentials");
        }
        
        // パスワード検証
        if (!user.getPassword().matches(rawPassword, passwordEncoder)) {
            throw new AuthenticationException("Invalid credentials");
        }
        
        return user;
    }
}
```

**学習ポイント**:
- ✅ **@Transactional(readOnly = true)**: 読み取り専用トランザクション
- ✅ **セキュリティ**: エラーメッセージを曖昧に（"Invalid credentials"）
- ✅ **柔軟性**: メールアドレスまたはユーザー名でログイン可能

### 4.3 UserQueryService

**ファイル**: `src/main/java/com/minislack/application/user/UserQueryService.java`

```java
package com.minislack.application.user;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minislack.domain.model.user.IUserRepository;
import com.minislack.domain.model.user.User;
import com.minislack.domain.model.user.UserId;

/**
 * ユーザー問い合わせサービス
 * 読み取り専用のユースケース
 */
@Service
public class UserQueryService {
    private final IUserRepository userRepository;

    public UserQueryService(@NonNull IUserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    @Transactional(readOnly = true)
    @NonNull
    public Optional<User> findById(@NonNull UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return userRepository.findById(userId);
    }

    @Transactional(readOnly = true)
    @NonNull
    public List<User> findAll() {
        return userRepository.findAll();
    }
}
```

**学習ポイント**:
- ✅ **CQRS風**: コマンド（書き込み）とクエリ（読み取り）を分離
- ✅ **読み取り専用**: `@Transactional(readOnly = true)`

---

## 5. チャンネル管理ユースケースの実装

### 5.1 CreateChannelCommand

**ファイル**: `src/main/java/com/minislack/application/channel/CreateChannelCommand.java`

```java
package com.minislack.application.channel;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * チャンネル作成コマンド
 */
public class CreateChannelCommand {
    private final String channelName;
    private final String description;
    private final boolean isPublic;
    private final String createdByUserId;

    public CreateChannelCommand(@NonNull String channelName, @Nullable String description, 
                               boolean isPublic, @NonNull String createdByUserId) {
        this.channelName = channelName;
        this.description = description;
        this.isPublic = isPublic;
        this.createdByUserId = createdByUserId;
    }

    @NonNull
    public String getChannelName() { return channelName; }
    
    @Nullable
    public String getDescription() { return description; }
    
    public boolean isPublic() { return isPublic; }
    
    @NonNull
    public String getCreatedByUserId() { return createdByUserId; }
}
```

### 5.2 ChannelManagementService

**ファイル**: `src/main/java/com/minislack/application/channel/ChannelManagementService.java`

```java
package com.minislack.application.channel;

import java.util.List;
import java.util.Objects;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minislack.domain.exception.DuplicateResourceException;
import com.minislack.domain.exception.ResourceNotFoundException;
import com.minislack.domain.model.channel.Channel;
import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.channel.ChannelMembership;
import com.minislack.domain.model.channel.ChannelName;
import com.minislack.domain.model.channel.Description;
import com.minislack.domain.model.channel.IChannelMembershipRepository;
import com.minislack.domain.model.channel.IChannelRepository;
import com.minislack.domain.model.channel.MembershipId;
import com.minislack.domain.model.user.UserId;
import com.minislack.domain.service.ChannelMembershipService;

/**
 * チャンネル管理アプリケーションサービス
 */
@Service
public class ChannelManagementService {
    private final IChannelRepository channelRepository;
    private final IChannelMembershipRepository membershipRepository;
    private final ChannelMembershipService membershipService;

    public ChannelManagementService(@NonNull IChannelRepository channelRepository,
                                   @NonNull IChannelMembershipRepository membershipRepository,
                                   @NonNull ChannelMembershipService membershipService) {
        this.channelRepository = Objects.requireNonNull(channelRepository);
        this.membershipRepository = Objects.requireNonNull(membershipRepository);
        this.membershipService = Objects.requireNonNull(membershipService);
    }

    /**
     * チャンネル作成
     */
    @Transactional
    @NonNull
    public ChannelId createChannel(@NonNull CreateChannelCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        
        // 1. 値オブジェクトの作成
        ChannelName channelName = new ChannelName(command.getChannelName());
        Description description = new Description(command.getDescription());
        UserId createdBy = UserId.of(command.getCreatedByUserId());

        // 2. 重複チェック
        if (channelRepository.existsByName(channelName)) {
            throw new DuplicateResourceException(
                "Channel name already exists: " + channelName.getValue()
            );
        }

        // 3. チャンネル作成
        Channel channel = new Channel(
            ChannelId.newId(),
            channelName,
            description,
            command.isPublic(),
            createdBy
        );

        Channel savedChannel = channelRepository.save(channel);

        // 4. 作成者を自動的にメンバーに追加
        ChannelMembership membership = new ChannelMembership(
            MembershipId.newId(),
            savedChannel.getChannelId(),
            createdBy
        );
        membershipRepository.save(membership);

        return savedChannel.getChannelId();
    }

    /**
     * チャンネル参加
     */
    @Transactional
    public void joinChannel(@NonNull UserId userId, @NonNull ChannelId channelId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
        
        // ドメインサービスに委譲
        membershipService.joinChannel(userId, channelId);
    }

    /**
     * チャンネル退出
     */
    @Transactional
    public void leaveChannel(@NonNull UserId userId, @NonNull ChannelId channelId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
        
        ChannelMembership membership = membershipRepository.findByChannelAndUser(channelId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Membership", 
                channelId.getValue() + ":" + userId.getValue()));
        
        membershipRepository.delete(membership);
    }

    /**
     * ユーザーが参加しているチャンネル一覧取得
     */
    @Transactional(readOnly = true)
    @NonNull
    public List<Channel> findChannelsByUser(@NonNull UserId userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return channelRepository.findByMember(userId);
    }

    /**
     * 公開チャンネル一覧取得
     */
    @Transactional(readOnly = true)
    @NonNull
    public List<Channel> findPublicChannels() {
        return channelRepository.findAllPublic();
    }
}
```

**学習ポイント**:
- ✅ **トランザクション管理**: `@Transactional`で一貫性保証
- ✅ **ドメインサービス活用**: 複雑なロジックはドメインサービスに委譲
- ✅ **CQRS**: 読み取り専用メソッドは`readOnly = true`

**処理フロー（チャンネル作成）**:
```text
1. コマンド受け取り
2. 値オブジェクト作成
3. 重複チェック
4. チャンネルエンティティ作成
5. 永続化
6. 作成者をメンバーに追加
7. チャンネルIDを返却
```

---

## 6. メッセージ管理ユースケースの実装

### 6.1 SendMessageCommand

**ファイル**: `src/main/java/com/minislack/application/message/SendMessageCommand.java`

```java
package com.minislack.application.message;

import org.springframework.lang.NonNull;

/**
 * メッセージ送信コマンド
 */
public class SendMessageCommand {
    private final String channelId;
    private final String content;

    public SendMessageCommand(@NonNull String channelId, @NonNull String content) {
        this.channelId = channelId;
        this.content = content;
    }

    @NonNull
    public String getChannelId() { return channelId; }
    
    @NonNull
    public String getContent() { return content; }
}
```

### 6.2 MessageService

**ファイル**: `src/main/java/com/minislack/application/message/MessageService.java`

```java
package com.minislack.application.message;

import java.util.List;
import java.util.Objects;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minislack.application.exception.AuthorizationException;
import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.channel.IChannelMembershipRepository;
import com.minislack.domain.model.message.IMessageRepository;
import com.minislack.domain.model.message.Message;
import com.minislack.domain.model.message.MessageContent;
import com.minislack.domain.model.message.MessageId;
import com.minislack.domain.model.user.UserId;

/**
 * メッセージアプリケーションサービス
 */
@Service
public class MessageService {
    private final IMessageRepository messageRepository;
    private final IChannelMembershipRepository membershipRepository;

    public MessageService(@NonNull IMessageRepository messageRepository,
                         @NonNull IChannelMembershipRepository membershipRepository) {
        this.messageRepository = Objects.requireNonNull(messageRepository);
        this.membershipRepository = Objects.requireNonNull(membershipRepository);
    }

    /**
     * メッセージ送信
     */
    @Transactional
    @NonNull
    public MessageId sendMessage(@NonNull SendMessageCommand command, @NonNull UserId senderId) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(senderId, "senderId must not be null");
        
        // 1. 値オブジェクトの作成
        ChannelId channelId = ChannelId.of(command.getChannelId());
        MessageContent content = new MessageContent(command.getContent());

        // 2. メンバーシップ確認（認可）
        if (!membershipRepository.existsByChannelAndUser(channelId, senderId)) {
            throw new AuthorizationException("User is not a member of this channel");
        }

        // 3. メッセージエンティティ作成
        Message message = new Message(MessageId.newId(), channelId, senderId, content);

        // 4. 永続化
        Message savedMessage = messageRepository.save(message);

        // 5. TODO: RabbitMQにイベント発行（後のハンズオンで実装）

        return savedMessage.getMessageId();
    }

    /**
     * チャンネルのメッセージ取得（ページネーション）
     */
    @Transactional(readOnly = true)
    @NonNull
    public List<Message> getMessages(@NonNull ChannelId channelId, @NonNull UserId userId, 
                                     int limit, int offset) {
        Objects.requireNonNull(channelId, "channelId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        
        // メンバーシップ確認
        if (!membershipRepository.existsByChannelAndUser(channelId, userId)) {
            throw new AuthorizationException("User is not a member of this channel");
        }
        
        return messageRepository.findByChannel(channelId, limit, offset);
    }

    /**
     * メッセージ検索
     */
    @Transactional(readOnly = true)
    @NonNull
    public List<Message> searchMessages(@NonNull String keyword, @NonNull UserId userId) {
        Objects.requireNonNull(keyword, "keyword must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        
        return messageRepository.searchByKeyword(keyword, userId);
    }
}
```

**学習ポイント**:
- ✅ **認可チェック**: メンバーのみがメッセージ送信・閲覧可能
- ✅ **ページネーション**: 大量データの効率的な取得
- ✅ **イベント発行予定**: RabbitMQとの連携は後で実装

---

## 7. アプリケーション層の例外

### 7.1 ApplicationException

**ファイル**: `src/main/java/com/minislack/application/exception/ApplicationException.java`

```java
package com.minislack.application.exception;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * アプリケーション層の基底例外
 */
public class ApplicationException extends RuntimeException {
    
    public ApplicationException(@NonNull String message) {
        super(message);
    }
    
    public ApplicationException(@NonNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
```

### 7.2 AuthenticationException

**ファイル**: `src/main/java/com/minislack/application/exception/AuthenticationException.java`

```java
package com.minislack.application.exception;

import org.springframework.lang.NonNull;

/**
 * 認証失敗例外
 */
public class AuthenticationException extends ApplicationException {
    public AuthenticationException(@NonNull String message) {
        super(message);
    }
}
```

### 7.3 AuthorizationException

**ファイル**: `src/main/java/com/minislack/application/exception/AuthorizationException.java`

```java
package com.minislack.application.exception;

import org.springframework.lang.NonNull;

/**
 * 認可失敗例外
 */
public class AuthorizationException extends ApplicationException {
    public AuthorizationException(@NonNull String message) {
        super(message);
    }
}
```

---

## 8. テスト実装

### 8.1 UserRegistrationServiceのテスト

**ファイル**: `src/test/java/com/minislack/application/user/UserRegistrationServiceTest.java`

```java
package com.minislack.application.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.minislack.domain.exception.DuplicateResourceException;
import com.minislack.domain.model.user.Email;
import com.minislack.domain.model.user.IPasswordEncoder;
import com.minislack.domain.model.user.IUserRepository;
import com.minislack.domain.model.user.User;
import com.minislack.domain.model.user.UserId;
import com.minislack.domain.model.user.Username;

class UserRegistrationServiceTest {
    
    private IUserRepository userRepository;
    private IPasswordEncoder passwordEncoder;
    private UserRegistrationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(IUserRepository.class);
        passwordEncoder = mock(IPasswordEncoder.class);
        service = new UserRegistrationService(userRepository, passwordEncoder);
    }

    @Test
    void registerUser_ValidCommand_ReturnsUserId() {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "testuser",
            "test@example.com",
            "password123",
            "Test User"
        );
        
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(userRepository.existsByUsername(any(Username.class))).thenReturn(false);
        when(passwordEncoder.encode(any(String.class))).thenReturn("hashed_password");
        
        User mockUser = mock(User.class);
        when(mockUser.getUserId()).thenReturn(UserId.newId());
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        
        // When
        UserId userId = service.registerUser(command);
        
        // Then
        assertNotNull(userId);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerUser_DuplicateEmail_ThrowsException() {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "testuser",
            "test@example.com",
            "password123",
            "Test User"
        );
        
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(true);
        
        // When & Then
        DuplicateResourceException exception = assertThrows(
            DuplicateResourceException.class,
            () -> service.registerUser(command)
        );
        
        assertEquals("Email already exists: test@example.com", exception.getMessage());
    }

    @Test
    void registerUser_DuplicateUsername_ThrowsException() {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "testuser",
            "test@example.com",
            "password123",
            "Test User"
        );
        
        when(userRepository.existsByEmail(any(Email.class))).thenReturn(false);
        when(userRepository.existsByUsername(any(Username.class))).thenReturn(true);
        
        // When & Then
        DuplicateResourceException exception = assertThrows(
            DuplicateResourceException.class,
            () -> service.registerUser(command)
        );
        
        assertEquals("Username already exists: testuser", exception.getMessage());
    }
}
```

**学習ポイント**:
- ✅ **Mockito**: リポジトリをモック化
- ✅ **Given-When-Then**: テストの構造化
- ✅ **例外検証**: `assertThrows()`で例外をテスト

---

## 9. まとめ

### 9.1 アプリケーション層実装の要点

1. **コマンドオブジェクト**:
   - ユースケースへの入力を表現
   - イミュータブル

2. **アプリケーションサービス**:
   - `@Service`アノテーション
   - `@Transactional`でトランザクション管理
   - ドメインオブジェクトのオーケストレーション

3. **ビジネスロジックの委譲**:
   - アプリケーション層はロジックを持たない
   - ドメイン層に委譲

4. **例外処理**:
   - ドメイン例外はそのまま伝播
   - アプリケーション固有の例外も定義

### 9.2 次のステップ

アプリケーション層の実装が完了しました！次はインフラ層を実装します：

- [08-infrastructure-layer.md](08-infrastructure-layer.md) - インフラ層実装

---

## 10. 参考資料

- [Application Services - DDD](https://enterprisecraftsmanship.com/posts/domain-vs-application-services/)
- [CQRS Pattern](https://martinfowler.com/bliki/CQRS.html)
- [Spring @Transactional](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html)

