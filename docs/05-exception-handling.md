# 例外処理設計 - オニオンアーキテクチャ

## 1. はじめに

このドキュメントでは、MiniSlackプロジェクトにおける例外処理の設計と実装方法を説明します。

### 1.1 例外処理の重要性

適切な例外処理は以下を実現します：

- **ユーザーへの明確なエラーメッセージ**
- **システムの安定性向上**
- **デバッグの容易性**
- **セキュリティの向上**（内部情報の漏洩防止）

### 1.2 オニオンアーキテクチャにおける例外処理の原則

各レイヤーには明確な責任があります：

| レイヤー | 責務 |
|---------|------|
| **Domain** | ビジネスルール違反を表現する例外を定義 |
| **Application** | ユースケース固有の例外、ドメイン例外の処理 |
| **Infrastructure** | 技術的例外をドメイン例外に変換 |
| **Presentation** | 例外をHTTPレスポンスに変換 |

---

## 2. 例外クラスの階層構造

### 2.1 全体像

```
RuntimeException
    ├── DomainException (ドメイン層)
    │   ├── BusinessRuleViolationException
    │   ├── ResourceNotFoundException
    │   └── DuplicateResourceException
    ├── ApplicationException (アプリケーション層)
    │   ├── AuthenticationException
    │   └── AuthorizationException
    └── InfrastructureException (インフラ層)
        ├── DataAccessException
        └── MessagingException
```

### 2.2 なぜRuntimeExceptionを継承するのか？

**理由1: Checked Exceptionは上位レイヤーに依存を強制する**

Checked Exceptionを使うと、すべての呼び出し元で`throws`句が必要になり、オニオンアーキテクチャの依存性ルールを守れなくなります。

**理由2: オニオンアーキテクチャの依存性ルールを守るため**

ドメイン層がフレームワーク非依存を保つためには、RuntimeExceptionが適しています。

**理由3: Spring Bootのトランザクション管理との相性が良い**

これが最も重要な理由です。詳しく説明します。

#### Spring Bootのトランザクション管理とRuntimeException

Spring Bootの`@Transactional`は、例外の種類によってロールバック動作が異なります：

| 例外の種類 | デフォルト動作 |
|-----------|-------------|
| **RuntimeException** | ✅ **自動ロールバック** |
| **Checked Exception** | ❌ **コミット（ロールバックしない）** |

**実際のコード例（RuntimeException - 推奨）**:

```java
@Service
public class UserRegistrationService {
    
    @Transactional  // ← rollbackForの指定が不要！
    public UserId registerUser(RegisterUserCommand command) {
        Email email = new Email(command.getEmail());
        
        // メール重複チェック
        if (userRepository.existsByEmail(email)) {
            // RuntimeException → 自動的にロールバック
            throw new DuplicateResourceException("Email already exists");
        }
        
        User user = new User(...);
        userRepository.save(user);  // ← 上でエラーなら保存されない
        
        return user.getUserId();
    }
}
```

**動作**:
1. `DuplicateResourceException`（RuntimeException）がスローされる
2. Spring Bootが自動的にトランザクションをロールバック
3. データベースへの保存がキャンセルされる

**Checked Exceptionの場合（非推奨）**:

```java
@Service
public class UserRegistrationService {
    
    // すべての例外を列挙する必要がある（面倒＆エラーの原因）
    @Transactional(rollbackFor = {
        DuplicateEmailException.class,
        InvalidUserDataException.class,
        // ... 増え続ける例外クラス
    })
    public UserId registerUser(...) throws DuplicateEmailException, 
                                           InvalidUserDataException {
        // ...
    }
}
```

**問題点**:
- ❌ すべてのメソッドで`rollbackFor`を指定する必要がある
- ❌ 新しい例外を追加する度に`rollbackFor`も更新が必要
- ❌ 指定漏れでバグが発生しやすい（データ不整合の原因）
- ❌ コードが冗長になる

#### トランザクション動作の違い

**RuntimeExceptionの場合（推奨）**:

```java
@Transactional
public void createUserAndChannel() {
    // 1. ユーザー作成
    User user = new User(...);
    userRepository.save(user);  // コミット前
    
    // 2. チャンネル作成
    Channel channel = new Channel(...);
    channelRepository.save(channel);  // コミット前
    
    // 3. メンバーシップ作成
    if (membershipRepository.existsByChannelAndUser(channelId, userId)) {
        // RuntimeException発生 → 自動ロールバック
        throw new DuplicateResourceException("Already a member");
    }
    
    // → トランザクション全体がロールバック
    // → user, channelも保存されない（データ整合性が保たれる）
}
```

**Checked Exceptionの場合（rollbackFor指定なし - 危険）**:

```java
@Transactional  // rollbackForの指定漏れ！
public void createUserAndChannel() throws DuplicateMemberException {
    // 1. ユーザー作成
    User user = new User(...);
    userRepository.save(user);  // コミット前
    
    // 2. チャンネル作成
    Channel channel = new Channel(...);
    channelRepository.save(channel);  // コミット前
    
    // 3. メンバーシップ作成
    if (membershipRepository.existsByChannelAndUser(channelId, userId)) {
        // Checked Exception発生 → デフォルトではコミット！
        throw new DuplicateMemberException("Already a member");
    }
    
    // → トランザクションがコミットされる！
    // → user, channelが保存される（データ不整合が発生！）
}
```

#### Spring Bootの内部動作（簡略版）

```java
// Spring Bootのトランザクション管理の内部実装（簡略版）
public Object invoke(MethodInvocation invocation) {
    TransactionStatus status = transactionManager.getTransaction(txAttr);
    Object result = null;
    try {
        result = invocation.proceed();  // メソッド実行
        transactionManager.commit(status);  // 正常終了 → コミット
    } catch (RuntimeException | Error ex) {
        // RuntimeExceptionとErrorは自動ロールバック
        transactionManager.rollback(status);
        throw ex;
    } catch (Throwable ex) {
        // Checked ExceptionはrollbackFor指定がなければコミット
        if (txAttr.rollbackOn(ex)) {
            transactionManager.rollback(status);
        } else {
            transactionManager.commit(status);  // ← 危険！
        }
        throw ex;
    }
    return result;
}
```

#### まとめ：RuntimeExceptionを使う理由

| 観点 | RuntimeException | Checked Exception |
|-----|-----------------|------------------|
| **ロールバック** | ✅ 自動 | ❌ 明示的指定が必要 |
| **設定** | ✅ `@Transactional`のみ | ❌ `rollbackFor`必須 |
| **保守性** | ✅ 新しい例外を追加しやすい | ❌ 常に`rollbackFor`更新が必要 |
| **安全性** | ✅ 設定漏れのリスクなし | ❌ 設定漏れでデータ不整合 |
| **シンプルさ** | ✅ コードが簡潔 | ❌ コードが冗長 |

**結論**: Spring Bootの`@Transactional`は**RuntimeExceptionで自動ロールバック**するため、ビジネスロジックのエラーをRuntimeExceptionで表現することで、トランザクション管理が自然かつ安全に行えます。

---

## 2.3 ビジネスルール違反 vs バリデーションエラーの違い

この2つはよく混同されますが、本質的に異なります。違いを理解することが重要です。

### 2.3.1 本質的な違い

| 観点 | バリデーションエラー | ビジネスルール違反 |
|-----|---------------------|-------------------|
| **何をチェック？** | **単一の値**の形式・範囲 | **複数のオブジェクト間の関係**や状態 |
| **いつチェック？** | **オブジェクト生成時** | **ビジネスロジック実行時** |
| **どこで実装？** | **値オブジェクト**のコンストラクタ | **エンティティ**のメソッドまたは**ドメインサービス** |
| **コンテキスト** | **コンテキスト不要**（値だけで判断） | **コンテキスト必要**（他のオブジェクトや状態が必要） |
| **例外** | `IllegalArgumentException` | `BusinessRuleViolationException` |
| **DB参照** | 不要 | 必要な場合が多い |

### 2.3.2 具体例1: メールアドレス

**バリデーションエラー（値オブジェクト）**:
```java
public class Email {
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    
    public Email(String value) {
        // 単一の値の形式チェック（コンテキスト不要）
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            // ✅ バリデーションエラー
            throw new IllegalArgumentException("Invalid email format");
        }
        this.value = value;
    }
}
```

**ビジネスルール違反（アプリケーションサービス）**:
```java
@Service
public class UserRegistrationService {
    
    @Transactional
    public UserId registerUser(RegisterUserCommand command) {
        Email email = new Email(command.getEmail());  // ← 形式チェック（バリデーション）
        
        // 他のオブジェクト（データベース）との関係チェック（コンテキスト必要）
        if (userRepository.existsByEmail(email)) {
            // ✅ ビジネスルール違反
            throw new BusinessRuleViolationException(
                "Email already exists in the system"
            );
        }
        
        // ...
    }
}
```

**違い**:
- バリデーション: `"test@example.com"`という文字列が**メールアドレスの形式として正しいか**
- ビジネスルール: `"test@example.com"`が**システム内で重複していないか**（データベース参照が必要）

### 2.3.3 具体例2: パスワード

**バリデーションエラー（値オブジェクト）**:
```java
public class Password {
    private static final int MIN_LENGTH = 8;
    
    public static Password fromRawPassword(String rawPassword, IPasswordEncoder encoder) {
        // 単純な文字数チェック（コンテキスト不要）
        if (rawPassword.length() < MIN_LENGTH) {
            // ✅ バリデーションエラー
            throw new IllegalArgumentException(
                "Password must be at least " + MIN_LENGTH + " characters"
            );
        }
        
        String hashed = encoder.encode(rawPassword);
        return new Password(hashed);
    }
}
```

**ビジネスルール違反（エンティティ）**:
```java
public class User {
    private Password password;
    
    public void changePassword(Password currentPassword, Password newPassword) {
        // 現在のパスワードとの比較（他のオブジェクト状態が必要）
        if (!this.password.matches(currentPassword)) {
            // ✅ ビジネスルール違反
            throw new BusinessRuleViolationException(
                "Current password is incorrect"
            );
        }
        
        // 新旧パスワードの比較（関係性のチェック）
        if (this.password.equals(newPassword)) {
            // ✅ ビジネスルール違反
            throw new BusinessRuleViolationException(
                "New password must be different from current password"
            );
        }
        
        this.password = newPassword;
    }
}
```

**違い**:
- バリデーション: パスワードが**8文字以上か**（値単体で判断）
- ビジネスルール: パスワードが**現在のパスワードと一致するか**（他の情報が必要）

### 2.3.4 具体例3: チャンネル名

**バリデーションエラー（値オブジェクト）**:
```java
public class ChannelName {
    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 50;
    
    public ChannelName(String value) {
        // 文字数の範囲チェック（コンテキスト不要）
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            // ✅ バリデーションエラー
            throw new IllegalArgumentException(
                "Channel name must be 4-50 characters"
            );
        }
        
        // 使用可能文字のチェック（コンテキスト不要）
        if (!value.matches("^[a-z0-9-]+$")) {
            // ✅ バリデーションエラー
            throw new IllegalArgumentException(
                "Channel name must contain only lowercase, numbers, and hyphens"
            );
        }
        
        this.value = value;
    }
}
```

**ビジネスルール違反（アプリケーションサービス）**:
```java
@Service
public class ChannelManagementService {
    
    @Transactional
    public ChannelId createChannel(CreateChannelCommand command, UserId creatorId) {
        ChannelName channelName = new ChannelName(command.getName());  // ← バリデーション
        
        // システム内での一意性チェック（データベース参照が必要）
        if (channelRepository.existsByName(channelName)) {
            // ✅ ビジネスルール違反
            throw new BusinessRuleViolationException(
                "Channel name already exists: " + channelName.getValue()
            );
        }
        
        // ユーザーが作成できるチャンネル数の制限（複数の情報が必要）
        long channelCount = channelRepository.countByCreator(creatorId);
        if (channelCount >= 10) {
            // ✅ ビジネスルール違反
            throw new BusinessRuleViolationException(
                "User has reached maximum channel creation limit (10)"
            );
        }
        
        // ...
    }
}
```

**違い**:
- バリデーション: 「general」という文字列が**チャンネル名として形式的に正しいか**
- ビジネスルール: 「general」という名前が**既に使われていないか**、**ユーザーがこれ以上作成できるか**

### 2.3.5 具体例4: メッセージ投稿

**バリデーションエラー（値オブジェクト）**:
```java
public class MessageContent {
    private static final int MAX_LENGTH = 2000;
    
    public MessageContent(String value) {
        // 空文字チェック（コンテキスト不要）
        if (value == null || value.isBlank()) {
            // ✅ バリデーションエラー
            throw new IllegalArgumentException(
                "Message content must not be empty"
            );
        }
        
        // 長さチェック（コンテキスト不要）
        if (value.length() > MAX_LENGTH) {
            // ✅ バリデーションエラー
            throw new IllegalArgumentException(
                "Message content must not exceed 2000 characters"
            );
        }
        
        this.value = value;
    }
}
```

**ビジネスルール違反（アプリケーションサービス）**:
```java
@Service
public class MessageService {
    
    @Transactional
    public MessageId sendMessage(SendMessageCommand command, UserId senderId) {
        ChannelId channelId = new ChannelId(command.getChannelId());
        MessageContent content = new MessageContent(command.getContent());  // ← バリデーション
        
        // チャンネルの存在確認（データベース参照が必要）
        Channel channel = channelRepository.findById(channelId)
            .orElseThrow(() -> new ResourceNotFoundException("Channel", channelId.getValue()));
        
        // 削除済みチャンネルへの投稿禁止（状態チェック）
        if (channel.isDeleted()) {
            // ✅ ビジネスルール違反
            throw new BusinessRuleViolationException(
                "Cannot send messages to a deleted channel"
            );
        }
        
        // メンバーシップ確認（複数オブジェクト間の関係）
        if (!membershipRepository.existsByChannelAndUser(channelId, senderId)) {
            // ✅ ビジネスルール違反
            throw new BusinessRuleViolationException(
                "User is not a member of this channel"
            );
        }
        
        // レート制限（時間的制約）
        long recentMessageCount = messageRepository.countByUserSince(
            senderId, 
            LocalDateTime.now().minusMinutes(1)
        );
        if (recentMessageCount >= 10) {
            // ✅ ビジネスルール違反
            throw new BusinessRuleViolationException(
                "Rate limit exceeded: maximum 10 messages per minute"
            );
        }
        
        // ...
    }
}
```

**違い**:
- バリデーション: メッセージ本文が**2000文字以内か**
- ビジネスルール: ユーザーが**そのチャンネルのメンバーか**、**チャンネルが削除されていないか**、**レート制限を超えていないか**

### 2.3.6 判断基準フローチャート

```
エラーをチェックしたい
    ↓
【質問1】単一の値だけで判断できる？
    YES → バリデーションエラー（値オブジェクト）
    NO → ↓
    
【質問2】他のオブジェクトや状態が必要？
    YES → ビジネスルール違反（エンティティ/ドメインサービス）
    NO → ↓
    
【質問3】データベースへの問い合わせが必要？
    YES → ビジネスルール違反（ドメインサービス/アプリケーションサービス）
    NO → バリデーションエラー（値オブジェクト）
```

### 2.3.7 実装場所の違い

**バリデーションエラー（値オブジェクト）**:

```java
// 値オブジェクトのコンストラクタ内
public class Username {
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
    
    public Username(String value) {
        // ここでバリデーション
        if (!VALID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Username must be 3-20 characters and contain only alphanumeric and underscore"
            );
        }
        this.value = value;
    }
}
```

**特徴**:
- ✅ オブジェクト生成時に自動的にチェック
- ✅ 不正な値を持つオブジェクトは存在できない（不変条件）
- ✅ 再利用可能（どこでも同じルール）
- ✅ データベースアクセス不要（高速）

**ビジネスルール違反（エンティティ/ドメインサービス）**:

```java
// エンティティのビジネスロジックメソッド内
public class User {
    public void changePassword(Password current, Password newPass) {
        // ここでビジネスルールチェック
        if (!this.password.matches(current)) {
            throw new BusinessRuleViolationException(
                "Current password is incorrect"
            );
        }
        this.password = newPass;
    }
}

// または、ドメインサービス内
public class ChannelMembershipService {
    public ChannelMembership joinChannel(UserId userId, ChannelId channelId) {
        // ここでビジネスルールチェック
        if (membershipRepository.existsByChannelAndUser(channelId, userId)) {
            throw new BusinessRuleViolationException(
                "User is already a member of this channel"
            );
        }
        // ...
    }
}
```

**特徴**:
- ✅ ビジネスロジック実行時にチェック
- ✅ 複数のオブジェクトの状態や関係を考慮
- ✅ コンテキスト依存
- ⚠️ データベースアクセスが必要な場合がある

### 2.3.8 より多くの具体例

#### 例1: ユーザー名

**バリデーション**:
```java
Username username = new Username("a");  
// ❌ IllegalArgumentException: "Username must be 3-20 characters"
// 理由: 文字数が足りない（値単体で判断）

Username username = new Username("user@name");  
// ❌ IllegalArgumentException: "Username must contain only alphanumeric and underscore"
// 理由: 使用禁止文字が含まれている（値単体で判断）
```

**ビジネスルール違反**:
```java
Username username = new Username("taro");  // ✅ 形式は正しい

// でも、既に使われている場合
if (userRepository.existsByUsername(username)) {
    // ❌ BusinessRuleViolationException: "Username already taken"
    // 理由: システム内で重複（データベース参照が必要）
    throw new BusinessRuleViolationException("Username already taken");
}
```

#### 例2: チャンネルへの参加

**バリデーション**:
```java
ChannelId channelId = new ChannelId("");
// ❌ IllegalArgumentException: "ChannelId must not be blank"
// 理由: 空文字（値単体で判断）

UserId userId = new UserId("invalid-uuid-format");
// ❌ IllegalArgumentException: "Invalid UUID format"
// 理由: UUID形式ではない（値単体で判断）
```

**ビジネスルール違反**:
```java
ChannelId channelId = new ChannelId("ch-123");  // ✅ 形式は正しい
UserId userId = new UserId("user-456");  // ✅ 形式は正しい

// ビジネスルールのチェック（複数オブジェクトの関係）
Channel channel = channelRepository.findById(channelId)
    .orElseThrow(() -> new ResourceNotFoundException("Channel", channelId.getValue()));

// ルール1: チャンネルが削除されていないか
if (channel.isDeleted()) {
    // ❌ BusinessRuleViolationException
    throw new BusinessRuleViolationException(
        "Cannot join deleted channel"
    );
}

// ルール2: プライベートチャンネルは招待が必要
if (!channel.isPublic() && !hasInvitation(userId, channelId)) {
    // ❌ BusinessRuleViolationException
    throw new BusinessRuleViolationException(
        "Private channel requires invitation"
    );
}

// ルール3: 既にメンバーでないか
if (membershipRepository.existsByChannelAndUser(channelId, userId)) {
    // ❌ BusinessRuleViolationException
    throw new BusinessRuleViolationException(
        "User is already a member of this channel"
    );
}

// ルール4: メンバー数上限チェック
if (membershipRepository.countByChannel(channelId) >= 100) {
    // ❌ BusinessRuleViolationException
    throw new BusinessRuleViolationException(
        "Channel has reached member limit (100)"
    );
}
```

#### 例3: 金額（より分かりやすい例）

**バリデーションエラー**:
```java
public class Amount {
    public Amount(BigDecimal value) {
        // 値の範囲チェック（値単体で判断）
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            // ✅ バリデーションエラー
            throw new IllegalArgumentException(
                "Amount cannot be negative"
            );
        }
        
        if (value.scale() > 2) {
            // ✅ バリデーションエラー
            throw new IllegalArgumentException(
                "Amount cannot have more than 2 decimal places"
            );
        }
        
        this.value = value;
    }
}
```

**ビジネスルール違反**:
```java
public class BankAccount {
    private Amount balance;
    
    public void withdraw(Amount amount) {
        // 残高との比較（他の状態が必要）
        if (this.balance.isLessThan(amount)) {
            // ✅ ビジネスルール違反
            throw new BusinessRuleViolationException(
                "Insufficient balance for withdrawal"
            );
        }
        
        this.balance = this.balance.subtract(amount);
    }
}
```

**違い**:
- バリデーション: `1000.00`という値が**金額として正しい形式か**（負数でない、小数点以下2桁以内）
- ビジネスルール: 口座から`1000.00`を**引き出せるだけの残高があるか**（残高との比較が必要）

### 2.3.9 ビジネスルール違反のパターン集

MiniSlackで発生する可能性のあるビジネスルール違反を分類：

#### パターン1: 状態遷移の違反

ビジネスルール: 「削除済みのチャンネルにはメッセージを送信できない」

```java
public class Channel {
    private ChannelStatus status;  // ACTIVE, ARCHIVED, DELETED
    
    public void verifyCanReceiveMessage() {
        if (this.status == ChannelStatus.DELETED) {
            throw new BusinessRuleViolationException(
                "Cannot send messages to a deleted channel"
            );
        }
    }
}
```

#### パターン2: 数量制限の違反

ビジネスルール: 「1つのチャンネルのメンバー数は最大100人まで」

```java
public class ChannelMembershipService {
    private static final int MAX_MEMBERS = 100;
    
    public ChannelMembership joinChannel(UserId userId, ChannelId channelId) {
        long currentMemberCount = membershipRepository.countByChannel(channelId);
        
        if (currentMemberCount >= MAX_MEMBERS) {
            throw new BusinessRuleViolationException(
                "Channel has reached maximum member limit (100)"
            );
        }
        
        // ... メンバー追加処理
    }
}
```

#### パターン3: 時間的制約の違反

ビジネスルール: 「メッセージは送信後24時間以内のみ削除可能」

```java
public class Message {
    private LocalDateTime createdAt;
    
    public void verifyCanDelete() {
        LocalDateTime now = LocalDateTime.now();
        Duration elapsed = Duration.between(createdAt, now);
        
        if (elapsed.toHours() > 24) {
            throw new BusinessRuleViolationException(
                "Messages can only be deleted within 24 hours of posting"
            );
        }
    }
}
```

#### パターン4: 権限の違反

ビジネスルール: 「チャンネル作成者のみがチャンネル情報を更新できる」

```java
public class Channel {
    private UserId createdBy;
    
    public void updateInfo(ChannelName newName, Description newDesc, UserId requesterId) {
        if (!this.createdBy.equals(requesterId)) {
            throw new BusinessRuleViolationException(
                "Only channel creator can update channel information"
            );
        }
        
        this.channelName = newName;
        this.description = newDesc;
        this.updatedAt = LocalDateTime.now();
    }
}
```

#### パターン5: 論理的整合性の違反

ビジネスルール: 「同じユーザーが同じチャンネルに複数回参加できない」

```java
public class ChannelMembershipService {
    
    public ChannelMembership joinChannel(UserId userId, ChannelId channelId) {
        // 既に参加している場合は違反
        if (membershipRepository.existsByChannelAndUser(channelId, userId)) {
            throw new BusinessRuleViolationException(
                "User is already a member of this channel"
            );
        }
        
        // ... メンバー追加処理
    }
}
```

#### パターン6: 依存関係の違反

ビジネスルール: 「メンバーがいるチャンネルは削除できない」

```java
public class ChannelDeletionService {
    
    public void deleteChannel(ChannelId channelId) {
        long memberCount = membershipRepository.countByChannel(channelId);
        
        if (memberCount > 0) {
            throw new BusinessRuleViolationException(
                "Cannot delete channel with active members. " +
                "Please remove all members first."
            );
        }
        
        channelRepository.delete(channelId);
    }
}
```

### 2.3.10 コンテキストの有無が決定的な違い

**バリデーション（コンテキスト不要）**:

```java
// これだけで判断できる
Email email = new Email("test@example.com");
// → 形式チェック（正規表現マッチ）のみ

Username username = new Username("taro");
// → 長さと文字種チェックのみ

ChannelName channelName = new ChannelName("general");
// → 長さと使用可能文字チェックのみ
```

**ビジネスルール（コンテキスト必要）**:

```java
// 他の情報が必要
if (userRepository.existsByEmail(email)) {  // ← データベースが必要
    throw new BusinessRuleViolationException(...);
}

if (!this.password.matches(currentPassword)) {  // ← 現在の状態が必要
    throw new BusinessRuleViolationException(...);
}

if (membershipRepository.countByChannel(channelId) >= 100) {  // ← 集計が必要
    throw new BusinessRuleViolationException(...);
}
```

### 2.3.11 まとめ表（決定版）

| | バリデーションエラー | ビジネスルール違反 |
|---|---------------------|-------------------|
| **チェック対象** | 値の形式・範囲 | オブジェクト間の関係・状態 |
| **判断基準** | 値だけで判断可能 | 他の情報が必要 |
| **実装場所** | 値オブジェクト | エンティティ/ドメインサービス |
| **タイミング** | オブジェクト生成時 | ビジネスロジック実行時 |
| **例外** | `IllegalArgumentException` | `BusinessRuleViolationException` |
| **DB参照** | 不要 | 必要な場合が多い |
| **テスト** | 超高速（Pure Java） | 高速（モック使用） |
| **例** | メール形式、文字数、正規表現 | 重複チェック、状態遷移、数量制限 |

**ポイント**: 
- **バリデーション** = 値が**技術的に正しいか**（形式、範囲）
- **ビジネスルール** = 値が**ビジネス的に許可されるか**（関係、状態、制約）

---

## 3. ドメイン層（Domain Layer）の例外

### 3.1 基底例外クラス

**ファイル**: `src/main/java/com/minislack/domain/exception/DomainException.java`

```java
package com.minislack.domain.exception;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * ドメイン層の基底例外
 * フレームワーク非依存（@NonNullはSpringだが許容）
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

### 3.2 ビジネスルール違反

**ファイル**: `src/main/java/com/minislack/domain/exception/BusinessRuleViolationException.java`

```java
package com.minislack.domain.exception;

import org.springframework.lang.NonNull;

/**
 * ビジネスルール違反例外
 * 
 * 使用例：
 * - パスワードが一致しない
 * - 無効な状態遷移
 * - ビジネスロジック上の制約違反
 */
public class BusinessRuleViolationException extends DomainException {
    
    public BusinessRuleViolationException(@NonNull String message) {
        super(message);
    }
}
```

**使用例**:

```java
public class User {
    public void changePassword(@NonNull Password currentPassword, 
                              @NonNull Password newPassword) {
        if (!this.password.matches(currentPassword)) {
            throw new BusinessRuleViolationException(
                "Current password is incorrect"
            );
        }
        this.password = Objects.requireNonNull(newPassword);
        this.updatedAt = LocalDateTime.now();
    }
}
```

### 3.3 リソース未検出

**ファイル**: `src/main/java/com/minislack/domain/exception/ResourceNotFoundException.java`

```java
package com.minislack.domain.exception;

import org.springframework.lang.NonNull;

/**
 * リソース未検出例外
 * 
 * 使用例：
 * - ユーザーが見つからない
 * - チャンネルが見つからない
 * - メッセージが見つからない
 */
public class ResourceNotFoundException extends DomainException {
    private final String resourceType;
    private final String resourceId;
    
    public ResourceNotFoundException(@NonNull String resourceType, 
                                     @NonNull String resourceId) {
        super(String.format("%s with id '%s' not found", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
    
    @NonNull
    public String getResourceType() { 
        return resourceType; 
    }
    
    @NonNull
    public String getResourceId() { 
        return resourceId; 
    }
}
```

**使用例**:

```java
public class ChannelMembershipService {
    @NonNull
    public ChannelMembership joinChannel(@NonNull UserId userId, 
                                         @NonNull ChannelId channelId) {
        Channel channel = channelRepository.findById(channelId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Channel", 
                channelId.getValue()
            ));
        
        // ... 処理続行
    }
}
```

### 3.4 重複リソース

**ファイル**: `src/main/java/com/minislack/domain/exception/DuplicateResourceException.java`

```java
package com.minislack.domain.exception;

import org.springframework.lang.NonNull;

/**
 * 重複リソース例外
 * 
 * 使用例：
 * - メールアドレスが既に登録されている
 * - ユーザー名が既に使用されている
 * - チャンネル名が重複している
 */
public class DuplicateResourceException extends DomainException {
    
    public DuplicateResourceException(@NonNull String message) {
        super(message);
    }
}
```

**使用例**:

```java
@Service
public class UserRegistrationService {
    @Transactional
    @NonNull
    public UserId registerUser(@NonNull RegisterUserCommand command) {
        Email email = new Email(command.getEmail());
        
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException(
                "Email already exists: " + email.getValue()
            );
        }
        
        // ... 処理続行
    }
}
```

---

## 4. アプリケーション層（Application Layer）の例外

### 4.1 基底例外クラス

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

### 4.2 認証例外

**ファイル**: `src/main/java/com/minislack/application/exception/AuthenticationException.java`

```java
package com.minislack.application.exception;

import org.springframework.lang.NonNull;

/**
 * 認証失敗例外
 * 
 * 使用例：
 * - ログイン失敗
 * - トークン無効
 * - パスワード不一致
 */
public class AuthenticationException extends ApplicationException {
    
    public AuthenticationException(@NonNull String message) {
        super(message);
    }
}
```

### 4.3 認可例外

**ファイル**: `src/main/java/com/minislack/application/exception/AuthorizationException.java`

```java
package com.minislack.application.exception;

import org.springframework.lang.NonNull;

/**
 * 認可失敗例外
 * 
 * 使用例：
 * - 権限不足
 * - リソースへのアクセス拒否
 * - 非公開チャンネルへのアクセス
 */
public class AuthorizationException extends ApplicationException {
    
    public AuthorizationException(@NonNull String message) {
        super(message);
    }
}
```

**使用例**:

```java
@Service
public class MessageService {
    @Transactional
    @NonNull
    public MessageId sendMessage(@NonNull SendMessageCommand command, 
                                 @NonNull UserId senderId) {
        ChannelId channelId = new ChannelId(command.getChannelId());
        
        // メンバーシップ確認
        if (!membershipRepository.existsByChannelAndUser(channelId, senderId)) {
            throw new AuthorizationException(
                "User is not a member of this channel"
            );
        }
        
        // ... 処理続行
    }
}
```

---

## 5. インフラ層（Infrastructure Layer）の例外

### 5.1 基底例外クラス

**ファイル**: `src/main/java/com/minislack/infrastructure/exception/InfrastructureException.java`

```java
package com.minislack.infrastructure.exception;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * インフラ層の基底例外
 */
public class InfrastructureException extends RuntimeException {
    
    public InfrastructureException(@NonNull String message) {
        super(message);
    }
    
    public InfrastructureException(@NonNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
```

### 5.2 データアクセス例外

**ファイル**: `src/main/java/com/minislack/infrastructure/exception/DataAccessException.java`

```java
package com.minislack.infrastructure.exception;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * データアクセス例外
 */
public class DataAccessException extends InfrastructureException {
    
    public DataAccessException(@NonNull String message) {
        super(message);
    }
    
    public DataAccessException(@NonNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
```

### 5.3 メッセージング例外

**ファイル**: `src/main/java/com/minislack/infrastructure/exception/MessagingException.java`

```java
package com.minislack.infrastructure.exception;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * メッセージキュー例外
 */
public class MessagingException extends InfrastructureException {
    
    public MessagingException(@NonNull String message) {
        super(message);
    }
    
    public MessagingException(@NonNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
```

### 5.4 リポジトリでの例外変換

**使用例**:

```java
@Repository
public class UserRepositoryImpl implements IUserRepository {
    private final SpringDataUserRepository jpaRepository;
    private final UserEntityMapper mapper;

    @Override
    @NonNull
    public User save(@NonNull User user) {
        try {
            UserJpaEntity entity = mapper.toJpaEntity(user);
            UserJpaEntity saved = jpaRepository.save(entity);
            return mapper.toDomain(saved);
        } catch (org.springframework.dao.DataAccessException e) {
            // Spring技術的例外をインフラ例外に変換
            throw new DataAccessException("Failed to save user", e);
        }
    }
}
```

---

## 6. プレゼンテーション層（Presentation Layer）の例外

### 6.1 エラーレスポンスDTO

**ファイル**: `src/main/java/com/minislack/presentation/api/dto/ErrorResponse.java`

```java
package com.minislack.presentation.api.dto;

import java.time.LocalDateTime;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 標準エラーレスポンス
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private final int status;
    private final String error;
    private final String message;
    private final LocalDateTime timestamp;

    public ErrorResponse(int status, @NonNull String error, 
                        @NonNull String message, @NonNull LocalDateTime timestamp) {
        this.status = status;
        this.error = error;
        this.message = message;
        this.timestamp = timestamp;
    }

    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
```

**ファイル**: `src/main/java/com/minislack/presentation/api/dto/ValidationErrorResponse.java`

```java
package com.minislack.presentation.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * バリデーションエラーレスポンス
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationErrorResponse {
    private final int status;
    private final String error;
    private final Map<String, String> fieldErrors;
    private final LocalDateTime timestamp;

    public ValidationErrorResponse(int status, @NonNull String error, 
                                   @NonNull Map<String, String> fieldErrors, 
                                   @NonNull LocalDateTime timestamp) {
        this.status = status;
        this.error = error;
        this.fieldErrors = fieldErrors;
        this.timestamp = timestamp;
    }

    public int getStatus() { return status; }
    public String getError() { return error; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
```

### 6.2 グローバル例外ハンドラー

**ファイル**: `src/main/java/com/minislack/presentation/api/exception/GlobalExceptionHandler.java`

```java
package com.minislack.presentation.api.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.minislack.application.exception.AuthenticationException;
import com.minislack.application.exception.AuthorizationException;
import com.minislack.domain.exception.BusinessRuleViolationException;
import com.minislack.domain.exception.DuplicateResourceException;
import com.minislack.domain.exception.ResourceNotFoundException;
import com.minislack.presentation.api.dto.ErrorResponse;
import com.minislack.presentation.api.dto.ValidationErrorResponse;

/**
 * グローバル例外ハンドラー
 * すべてのコントローラーで発生した例外をキャッチしてHTTPレスポンスに変換
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * リソースが見つからない (404 Not Found)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            @NonNull ResourceNotFoundException ex) {
        logger.warn("Resource not found: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "Resource Not Found",
            ex.getMessage(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * 重複リソース (409 Conflict)
     */
    @ExceptionHandler(DuplicateResourceException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            @NonNull DuplicateResourceException ex) {
        logger.warn("Duplicate resource: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            HttpStatus.CONFLICT.value(),
            "Duplicate Resource",
            ex.getMessage(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * ビジネスルール違反 (400 Bad Request)
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleBusinessRuleViolation(
            @NonNull BusinessRuleViolationException ex) {
        logger.warn("Business rule violation: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Business Rule Violation",
            ex.getMessage(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * バリデーションエラー (400 Bad Request)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @NonNull
    public ResponseEntity<ValidationErrorResponse> handleValidationErrors(
            @NonNull MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        logger.warn("Validation failed: {}", errors);
        
        ValidationErrorResponse response = new ValidationErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Validation Failed",
            errors,
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 認証エラー (401 Unauthorized)
     */
    @ExceptionHandler(AuthenticationException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleAuthentication(
            @NonNull AuthenticationException ex) {
        logger.warn("Authentication failed: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            HttpStatus.UNAUTHORIZED.value(),
            "Authentication Failed",
            ex.getMessage(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    /**
     * 認可エラー (403 Forbidden)
     */
    @ExceptionHandler(AuthorizationException.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleAuthorization(
            @NonNull AuthorizationException ex) {
        logger.warn("Authorization failed: {}", ex.getMessage());
        
        ErrorResponse error = new ErrorResponse(
            HttpStatus.FORBIDDEN.value(),
            "Authorization Failed",
            ex.getMessage(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * その他の予期しないエラー (500 Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    @NonNull
    public ResponseEntity<ErrorResponse> handleGenericException(
            @NonNull Exception ex) {
        logger.error("Unexpected error occurred", ex);
        
        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "An unexpected error occurred",
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

---

## 7. 例外処理のフロー図

```
┌─────────────────────────────────────────────────────────┐
│ 1. Controller (Presentation Layer)                     │
│    - HTTPリクエスト受信                                 │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│ 2. Application Service (Application Layer)             │
│    - ビジネスロジック実行                               │
│    - DuplicateResourceException スロー                  │
└────────────────────┬────────────────────────────────────┘
                     │ throw
                     ▼
┌─────────────────────────────────────────────────────────┐
│ 3. GlobalExceptionHandler (Presentation Layer)         │
│    - 例外をキャッチ                                     │
│    - HTTPステータスコード: 409 Conflict                 │
│    - ErrorResponseに変換                                │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│ 4. HTTPレスポンス                                       │
│    {                                                    │
│      "status": 409,                                     │
│      "error": "Duplicate Resource",                     │
│      "message": "Email already exists: test@example.com"│
│      "timestamp": "2025-11-01T12:34:56"                 │
│    }                                                    │
└─────────────────────────────────────────────────────────┘
```

---

## 8. HTTPステータスコードマッピング

| 例外クラス | HTTPステータス | 説明 |
|-----------|---------------|------|
| `ResourceNotFoundException` | 404 Not Found | リソースが見つからない |
| `DuplicateResourceException` | 409 Conflict | 重複リソース |
| `BusinessRuleViolationException` | 400 Bad Request | ビジネスルール違反 |
| `MethodArgumentNotValidException` | 400 Bad Request | バリデーションエラー |
| `AuthenticationException` | 401 Unauthorized | 認証失敗 |
| `AuthorizationException` | 403 Forbidden | 認可失敗 |
| `IllegalArgumentException` | 400 Bad Request | 不正な引数 |
| `Exception` (その他) | 500 Internal Server Error | サーバーエラー |

---

## 9. ベストプラクティス

### 9.1 ドメイン層

✅ **推奨**:
```java
// フレームワーク非依存
throw new BusinessRuleViolationException("Password must not be empty");
```

❌ **非推奨**:
```java
// Springに依存
throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "...");
```

### 9.2 具体的な例外クラスを使用

✅ **推奨**:
```java
throw new ResourceNotFoundException("User", userId.getValue());
```

❌ **非推奨**:
```java
throw new RuntimeException("User not found");
```

### 9.3 例外にコンテキスト情報を含める

✅ **推奨**:
```java
public class ResourceNotFoundException extends DomainException {
    private final String resourceType;
    private final String resourceId;
    // ...
}
```

❌ **非推奨**:
```java
throw new RuntimeException("Not found");
```

### 9.4 ログ出力のレベル

```java
// WARN: ビジネスロジック上の予期されたエラー
logger.warn("User not found: {}", userId);

// ERROR: 予期しないシステムエラー
logger.error("Database connection failed", exception);

// INFO: 正常な処理フロー
logger.info("User registered successfully: {}", userId);
```

### 9.5 セキュリティ上の注意

❌ **機密情報を含めない**:
```java
// パスワード、トークン、内部パス等を含めない
throw new AuthenticationException(
    "Login failed for user: " + username + " with password: " + password
);
```

✅ **推奨**:
```java
throw new AuthenticationException("Invalid credentials");
```

---

## 10. テストでの例外検証

### 10.1 JUnit 5での例外テスト

```java
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.minislack.domain.exception.DuplicateResourceException;
import com.minislack.domain.model.user.Email;

class UserRegistrationServiceTest {

    @Test
    void registerUser_EmailAlreadyExists_ThrowsDuplicateResourceException() {
        // Given
        Email email = new Email("test@example.com");
        when(userRepository.existsByEmail(email)).thenReturn(true);

        // When & Then
        DuplicateResourceException exception = assertThrows(
            DuplicateResourceException.class,
            () -> registrationService.registerUser(command)
        );
        
        assertEquals("Email already exists: test@example.com", exception.getMessage());
    }
}
```

### 10.2 MockMvcでのHTTPレスポンステスト

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void register_DuplicateEmail_Returns409Conflict() throws Exception {
        mockMvc.perform(post("/api/v1/users/register")
                .contentType("application/json")
                .content("{\"email\":\"test@example.com\",\"username\":\"test\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.error").value("Duplicate Resource"))
            .andExpect(jsonPath("$.message").exists());
    }
}
```

---

## 11. まとめ

### 11.1 例外処理の設計原則

1. **レイヤーごとに適切な例外を定義**
   - Domain: ビジネスルール違反
   - Application: ユースケース固有
   - Infrastructure: 技術的詳細
   - Presentation: HTTPレスポンス変換

2. **依存性の方向を守る**
   - ドメイン層はフレームワーク非依存
   - 外側から内側への依存のみ

3. **具体的で意味のある例外を使用**
   - `ResourceNotFoundException` > `RuntimeException`

4. **適切なログ出力**
   - ビジネスエラー: WARN
   - システムエラー: ERROR

5. **セキュリティを考慮**
   - 機密情報を含めない
   - 内部実装の詳細を隠蔽

### 11.2 次のステップ

例外処理の基本設計が理解できたら、次は各レイヤーの実装に進みます：

- [06-domain-layer.md](06-domain-layer.md) - ドメイン層実装
- [07-application-layer.md](07-application-layer.md) - アプリケーション層実装

---

## 12. 参考資料

- [Spring Boot Exception Handling](https://spring.io/blog/2013/11/01/exception-handling-in-spring-mvc)
- [Clean Architecture - Error Handling](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Java Exception Best Practices](https://www.baeldung.com/java-exceptions)
