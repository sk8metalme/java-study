# オニオンアーキテクチャ概要

## 1. はじめに

このドキュメントでは、MiniSlackプロジェクトで採用する**オニオンアーキテクチャ（Onion Architecture）**について説明します。

### 1.1 なぜアーキテクチャが重要なのか？

プログラミング初心者の頃は、全てのコードを1つのファイルに書いたり、思いついた順に機能を追加したりすることがあります。しかし、プロジェクトが大きくなると：

- **コードの変更が困難になる**：どこに何があるか分からない
- **テストが書けない**：依存関係が複雑すぎる
- **再利用できない**：ビジネスロジックがフレームワークと密結合
- **チーム開発が難しい**：役割分担ができない

アーキテクチャは、これらの問題を解決するための「設計図」です。

### 1.2 オニオンアーキテクチャとは？

オニオンアーキテクチャは、Jeffrey Palermo氏が2008年に提唱したアーキテクチャパターンです。名前の通り、**玉ねぎのように同心円状のレイヤー構造**を持ちます。

**特徴**:
- ビジネスロジック（ドメイン）を中心に配置
- 外側のレイヤーは内側に依存できるが、その逆は禁止
- フレームワークやデータベースは外側（詳細）として扱う

**類似アーキテクチャ**:
- Clean Architecture（Robert C. Martin）
- Hexagonal Architecture / Ports & Adapters（Alistair Cockburn）

これらは細部は異なりますが、**「ビジネスロジックを中心に据え、技術的詳細を外側に配置する」**という点で共通しています。

---

## 2. レイヤー構造

MiniSlackでは、以下の4つのレイヤーを定義します：

```
┌─────────────────────────────────────────┐
│   Presentation Layer                    │  ← 最も外側
│   (Controllers, DTOs, Mappers)          │
├─────────────────────────────────────────┤
│   Infrastructure Layer                  │
│   (Repositories, External APIs,         │
│    Message Queue, Database)             │
├─────────────────────────────────────────┤
│   Application Layer                     │
│   (Use Cases, Services)                 │
├─────────────────────────────────────────┤
│   Domain Layer                          │  ← 最も内側（核心）
│   (Entities, Value Objects,             │
│    Domain Services, Interfaces)         │
└─────────────────────────────────────────┘
```

### 2.1 依存性の方向ルール

**最重要ルール: 依存性は外側から内側へのみ**

```
Presentation → Application → Domain
     ↓              ↓
Infrastructure → Application
```

**OK（許可される依存）**:
- ✅ Presentation → Application
- ✅ Presentation → Domain
- ✅ Application → Domain
- ✅ Infrastructure → Domain

**NG（禁止される依存）**:
- ❌ Domain → Application
- ❌ Domain → Infrastructure
- ❌ Domain → Presentation
- ❌ Application → Infrastructure（直接は禁止）

### 2.2 なぜこのルールが重要なのか？

**例: データベースを変更する場合**

従来のレイヤードアーキテクチャ（3層アーキテクチャ）では、ビジネスロジックがデータベースに依存していることが多く、データベースを変更するとビジネスロジックも変更が必要でした。

オニオンアーキテクチャでは：
- ドメイン層は「インターフェース（契約）」のみを定義
- インフラ層がそのインターフェースを実装
- データベースを変更してもドメイン層は変更不要

**依存性逆転の原則（DIP: Dependency Inversion Principle）**

```java
// ❌ 悪い例: ドメインがインフラに依存
public class UserService {
    private UserRepository repository; // 具体的なDB実装クラス
    
    public void registerUser(User user) {
        repository.save(user); // DB実装に依存
    }
}

// ✅ 良い例: ドメインはインターフェースのみ知る
public class UserService {
    private IUserRepository repository; // インターフェース
    
    public void registerUser(User user) {
        repository.save(user); // 実装の詳細は知らない
    }
}
```

---

## 3. 各レイヤーの詳細

### 3.1 Domain Layer（ドメイン層）

**責務**: ビジネスルールとビジネスロジックの定義

**含まれるもの**:
- **エンティティ（Entities）**: ビジネスの中核概念を表すオブジェクト
- **値オブジェクト（Value Objects）**: 不変で等価性で比較されるオブジェクト
- **ドメインサービス（Domain Services）**: エンティティに属さないビジネスロジック
- **リポジトリインターフェース**: データ永続化の契約
- **ドメインイベント**: ビジネス上重要な出来事

**特徴**:
- **フレームワーク非依存**: Spring等のアノテーションは使わない（可能な限り）
- **外部ライブラリ非依存**: Pure Javaで記述
- **テスト容易性**: モックなしでテスト可能

**例: Userエンティティ**

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

    // コンストラクタ: 不変条件を保証
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

    // ビジネスロジック: パスワード変更
    public void changePassword(@NonNull Password currentPassword, @NonNull Password newPassword) {
        if (!this.password.matches(currentPassword)) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        this.password = Objects.requireNonNull(newPassword, "newPassword must not be null");
        this.updatedAt = LocalDateTime.now();
    }

    // ゲッター（イミュータブルな値オブジェクトを返す）
    @NonNull
    public UserId getUserId() { return userId; }
    
    @NonNull
    public Username getUsername() { return username; }
    
    @NonNull
    public Email getEmail() { return email; }
    
    @NonNull
    public DisplayName getDisplayName() { return displayName; }
    
    @NonNull
    public LocalDateTime getCreatedAt() { return createdAt; }
    
    @NonNull
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
```

**例: リポジトリインターフェース**

```java
package com.minislack.domain.model.user;

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
}
```

### 3.2 Application Layer（アプリケーション層）

**責務**: ユースケース（利用シナリオ）の実行

**含まれるもの**:
- **アプリケーションサービス**: ユースケースの実装
- **コマンド/クエリオブジェクト**: 入力データの表現
- **DTOマッパー**: エンティティとDTOの変換

**特徴**:
- **トランザクション境界**: ユースケース単位でトランザクション管理
- **ドメインのオーケストレーション**: 複数のドメインオブジェクトを協調させる
- **ビジネスロジックは持たない**: ドメイン層に委譲

**例: ユーザー登録ユースケース**

```java
package com.minislack.application.user;

import java.util.Objects;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        
        // 1. 値オブジェクトの作成
        Username username = new Username(command.getUsername());
        Email email = new Email(command.getEmail());
        DisplayName displayName = new DisplayName(command.getDisplayName());

        // 2. 重複チェック（ビジネスルール）
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        // 3. パスワードのハッシュ化
        Password password = Password.fromRawPassword(
            command.getPassword(), 
            passwordEncoder
        );

        // 4. エンティティの作成
        User user = new User(
            UserId.newId(),
            username,
            email,
            password,
            displayName
        );

        // 5. 永続化
        User savedUser = userRepository.save(user);

        return savedUser.getUserId();
    }
}
```

### 3.3 Infrastructure Layer（インフラストラクチャ層）

**責務**: 技術的詳細の実装

**含まれるもの**:
- **リポジトリ実装**: JPA/Hibernateを使ったDB操作
- **外部API連携**: REST API呼び出し等
- **メッセージキュー**: RabbitMQの実装
- **ファイルシステム**: ファイル入出力

**特徴**:
- **技術スタックに依存**: Spring Data JPA、RabbitMQ等
- **ドメインインターフェースを実装**: 依存性逆転を実現

**例: ユーザーリポジトリ実装（JPA）**

```java
package com.minislack.infrastructure.persistence.user;

import java.util.Objects;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.minislack.domain.model.user.Email;
import com.minislack.domain.model.user.IUserRepository;
import com.minislack.domain.model.user.User;
import com.minislack.domain.model.user.UserId;
import com.minislack.domain.model.user.Username;

/**
 * JPA用のSpring Data Repository
 */
interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, String> {
    @NonNull
    Optional<UserJpaEntity> findByEmail(@NonNull String email);
    
    @NonNull
    Optional<UserJpaEntity> findByUsername(@NonNull String username);
    
    boolean existsByEmail(@NonNull String email);
    
    boolean existsByUsername(@NonNull String username);
}

/**
 * ドメインリポジトリインターフェースの実装
 * Spring Data JPAをラップする
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
    public boolean existsByEmail(@NonNull Email email) {
        Objects.requireNonNull(email, "email must not be null");
        return jpaRepository.existsByEmail(email.getValue());
    }

    @Override
    @NonNull
    public Optional<User> findByUsername(@NonNull Username username) {
        Objects.requireNonNull(username, "username must not be null");
        return jpaRepository.findByUsername(username.getValue())
                           .map(mapper::toDomain);
    }

    @Override
    public boolean existsByUsername(@NonNull Username username) {
        Objects.requireNonNull(username, "username must not be null");
        return jpaRepository.existsByUsername(username.getValue());
    }
}
```

### 3.4 Presentation Layer（プレゼンテーション層）

**責務**: 外部とのインターフェース

**含まれるもの**:
- **RESTコントローラー**: HTTPリクエスト/レスポンス処理
- **DTOs（Data Transfer Objects）**: API入出力の表現
- **バリデーション**: 入力検証
- **エラーハンドリング**: 例外のHTTPレスポンス変換

**特徴**:
- **HTTPに依存**: Spring Web MVC
- **薄いレイヤー**: ビジネスロジックは持たない

**例: ユーザー登録コントローラー**

```java
package com.minislack.presentation.api.user;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minislack.application.user.RegisterUserCommand;
import com.minislack.application.user.UserRegistrationService;
import com.minislack.domain.model.user.UserId;

import jakarta.validation.Valid;

/**
 * ユーザー関連のREST APIコントローラー
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserRegistrationService registrationService;

    public UserController(@NonNull UserRegistrationService registrationService) {
        this.registrationService = Objects.requireNonNull(registrationService);
    }

    @PostMapping("/register")
    @NonNull
    public ResponseEntity<UserRegistrationResponse> register(
            @Valid @RequestBody @NonNull UserRegistrationRequest request) {
        
        // DTOをコマンドオブジェクトに変換
        RegisterUserCommand command = new RegisterUserCommand(
            request.getUsername(),
            request.getEmail(),
            request.getPassword(),
            request.getDisplayName()
        );

        // アプリケーションサービスを呼び出し
        UserId userId = registrationService.registerUser(command);

        // レスポンスDTOに変換
        UserRegistrationResponse response = new UserRegistrationResponse(
            userId.getValue(),
            "User registered successfully"
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
```

---

## 4. パッケージ構成

MiniSlackのパッケージ構造は以下のようになります：

```
com.minislack/
├── domain/                          # ドメイン層
│   ├── model/
│   │   ├── user/
│   │   │   ├── User.java           # エンティティ
│   │   │   ├── UserId.java         # 値オブジェクト
│   │   │   ├── Username.java       # 値オブジェクト
│   │   │   ├── Email.java          # 値オブジェクト
│   │   │   ├── Password.java       # 値オブジェクト
│   │   │   ├── DisplayName.java    # 値オブジェクト
│   │   │   └── IUserRepository.java # リポジトリIF
│   │   ├── channel/
│   │   │   ├── Channel.java
│   │   │   ├── ChannelId.java
│   │   │   └── IChannelRepository.java
│   │   └── message/
│   │       ├── Message.java
│   │       ├── MessageId.java
│   │       └── IMessageRepository.java
│   └── service/                     # ドメインサービス
│       └── IPasswordEncoder.java    # 暗号化インターフェース
│
├── application/                     # アプリケーション層
│   ├── user/
│   │   ├── UserRegistrationService.java
│   │   ├── UserAuthenticationService.java
│   │   ├── RegisterUserCommand.java
│   │   └── AuthenticateUserCommand.java
│   ├── channel/
│   │   ├── ChannelManagementService.java
│   │   ├── CreateChannelCommand.java
│   │   └── JoinChannelCommand.java
│   └── message/
│       ├── MessageService.java
│       └── SendMessageCommand.java
│
├── infrastructure/                  # インフラ層
│   ├── persistence/
│   │   ├── user/
│   │   │   ├── UserRepositoryImpl.java
│   │   │   ├── UserJpaEntity.java  # JPAエンティティ
│   │   │   └── UserEntityMapper.java
│   │   ├── channel/
│   │   └── message/
│   ├── messaging/
│   │   ├── rabbitmq/
│   │   │   ├── RabbitMQConfig.java
│   │   │   ├── MessagePublisher.java
│   │   │   └── MessageConsumer.java
│   └── security/
│       └── BCryptPasswordEncoder.java
│
└── presentation/                    # プレゼンテーション層
    ├── api/
    │   ├── user/
    │   │   ├── UserController.java
    │   │   ├── UserRegistrationRequest.java
    │   │   └── UserRegistrationResponse.java
    │   ├── channel/
    │   └── message/
    └── web/
        └── WebController.java       # Thymeleafコントローラー
```

---

## 5. データフロー例：ユーザー登録

ユーザー登録APIが呼ばれた時のデータフローを追ってみましょう。

```
1. HTTPリクエスト
   POST /api/v1/users/register
   Body: {"username":"taro","email":"taro@example.com","password":"pass1234","displayName":"太郎"}

2. Presentation Layer
   UserController.register()
   ↓ (UserRegistrationRequest → RegisterUserCommand)

3. Application Layer
   UserRegistrationService.registerUser()
   ↓ (コマンド → ドメインオブジェクト作成)
   ↓ (重複チェック)
   ↓ (エンティティ作成)
   ↓ (リポジトリ呼び出し)

4. Infrastructure Layer
   UserRepositoryImpl.save()
   ↓ (User → UserJpaEntity)
   ↓ (JPA save)
   ↓ (UserJpaEntity → User)

5. Application Layer
   ← (UserIdを返却)

6. Presentation Layer
   ← (UserId → UserRegistrationResponse)

7. HTTPレスポンス
   201 Created
   Body: {"userId":1,"message":"User registered successfully"}
```

**各レイヤーの役割**:
- **Presentation**: HTTPとJavaオブジェクトの変換
- **Application**: ユースケースの実行（オーケストレーション）
- **Domain**: ビジネスルールの検証
- **Infrastructure**: データベースへの永続化

---

## 6. オニオンアーキテクチャのメリット

### 6.1 テスト容易性

**ドメイン層のテスト**（モック不要）:
```java
@Test
void testPasswordChange() {
    // モックなしでテスト可能
    User user = new User(/* ... */);
    Password newPassword = new Password(/* ... */);
    
    user.changePassword(currentPassword, newPassword);
    
    assertTrue(user.getPassword().matches(newPassword));
}
```

**アプリケーション層のテスト**（リポジトリをモック）:
```java
@Test
void testUserRegistration() {
    IUserRepository mockRepo = mock(IUserRepository.class);
    when(mockRepo.existsByEmail(any())).thenReturn(false);
    
    UserRegistrationService service = new UserRegistrationService(mockRepo, encoder);
    UserId userId = service.registerUser(command);
    
    verify(mockRepo).save(any(User.class));
}
```

### 6.2 技術スタックの変更が容易

**例: データベースをMySQLからPostgreSQLに変更**
- Infrastructure層のみ変更
- Domain、Application、Presentation層は無変更

**例: Spring BootからQuarkusに移行**
- Infrastructure、Presentation層を変更
- Domain、Application層は無変更（Pure Javaなので）

### 6.3 ビジネスロジックの独立性

- ドメイン層はフレームワーク非依存
- ビジネスルールの変更時、影響範囲が明確
- 複数のプロジェクトで同じドメイン層を再利用可能

### 6.4 チーム開発の効率化

- レイヤーごとに担当を分けられる
  - ドメインエキスパート: ドメイン層
  - バックエンドエンジニア: Application/Infrastructure層
  - フロントエンドエンジニア: Presentation層（API仕様が決まれば並行作業可能）

---

## 7. よくある質問

### Q1. レイヤーが多くて複雑では？

**A**: 最初は確かに複雑に感じますが、以下のメリットがあります：
- 変更箇所が明確（責任の分離）
- テストが書きやすい
- 長期的な保守性が高い

小規模プロジェクトでは過剰かもしれませんが、学習目的では最適です。

### Q2. エンティティとJPAエンティティを分ける必要がある？

**A**: はい、以下の理由で分けます：
- **ドメインエンティティ**: ビジネスロジックを持つ、フレームワーク非依存
- **JPAエンティティ**: データベーステーブルのマッピング、JPA/Hibernateに依存

変換コストはありますが、ドメインの独立性が保たれます。

### Q3. インフラ層がドメイン層に依存するのはなぜ？

**A**: リポジトリインターフェース（ドメイン層）を実装するためです。これが**依存性逆転の原則（DIP）**です。

```
従来: ドメイン → リポジトリ実装（インフラ）
オニオン: ドメイン ← リポジトリ実装（インフラ）
          ↑定義    ↑実装
```

### Q4. DTO、Entity、Value Object、JPA Entityの違いは？

| 種類 | 目的 | 配置場所 | 特徴 |
|-----|------|---------|------|
| **DTO** | データ転送 | Presentation | シリアライズ可能、バリデーション |
| **Entity** | ビジネスモデル | Domain | ビジネスロジック、識別子あり |
| **Value Object** | 値の表現 | Domain | 不変、等価性で比較 |
| **JPA Entity** | DBマッピング | Infrastructure | @Entity, @Table等のアノテーション |

---

## 8. 実装時の注意点

### 8.1 依存性の方向を守る

```java
// ❌ 悪い例: ドメインがインフラに依存
package com.minislack.domain.model.user;
import com.minislack.infrastructure.persistence.UserJpaEntity; // NG!

// ✅ 良い例: ドメインは自己完結
package com.minislack.domain.model.user;
// インフラへのimportなし
```

### 8.2 ドメイン層はPure Javaで

```java
// ❌ 悪い例: ドメインにSpringアノテーション
@Component // NG! Springに依存
public class User {
    // ...
}

// ✅ 良い例: アノテーションなし
public class User {
    // Pure Java
}
```

### 8.3 アプリケーション層でトランザクション管理

```java
// ✅ アプリケーション層でトランザクション境界
@Service
public class UserRegistrationService {
    @Transactional // ユースケース単位
    public UserId registerUser(RegisterUserCommand command) {
        // ...
    }
}
```

---

## 9. まとめ

オニオンアーキテクチャの要点：

1. **4つのレイヤー**: Domain（核心）、Application、Infrastructure、Presentation
2. **依存性ルール**: 外側→内側のみ
3. **ドメイン駆動**: ビジネスロジックを中心に設計
4. **依存性逆転**: インターフェースで抽象化
5. **技術非依存**: ドメインはフレームワーク非依存

次のドキュメントでは、MiniSlackのドメインモデルを詳細に設計します。

---

## 10. 参考資料

- [The Onion Architecture](https://jeffreypalermo.com/2008/07/the-onion-architecture-part-1/) - Jeffrey Palermo
- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html) - Robert C. Martin
- [Domain-Driven Design](https://www.domainlanguage.com/ddd/) - Eric Evans
- [Dependency Inversion Principle](https://en.wikipedia.org/wiki/Dependency_inversion_principle)

