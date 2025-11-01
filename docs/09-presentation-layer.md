# プレゼンテーション層実装ハンズオン

## 1. はじめに

このドキュメントでは、MiniSlackのプレゼンテーション層を実装していきます。

### 1.1 プレゼンテーション層とは？

**プレゼンテーション層**は、**外部とのインターフェース**を担当するレイヤーです。

**責務**:
- HTTP リクエスト/レスポンスの処理
- DTOとドメインオブジェクトの変換
- 入力バリデーション
- 例外のHTTPレスポンス変換

**特徴**:
- 薄いレイヤー（ビジネスロジックなし）
- Spring Web MVCに依存
- REST APIの実装

---

## 2. ディレクトリ構造

```text
src/main/java/com/minislack/presentation/
├── api/
│   ├── user/          # ユーザーAPI
│   ├── channel/       # チャンネルAPI
│   ├── message/       # メッセージAPI
│   ├── dto/           # 共通DTO
│   └── exception/     # 例外ハンドラー
└── web/               # Webコントローラー（Thymeleaf）
```

---

## 3. DTO（Data Transfer Object）の実装

### 3.1 DTOとは？

**DTO**は、API入出力のデータを表現するオブジェクトです。

**特徴**:
- シリアライズ可能（JSON変換）
- バリデーションアノテーション
- ドメインオブジェクトとは独立

### 3.2 ユーザー登録リクエストDTO

**ファイル**: `src/main/java/com/minislack/presentation/api/user/UserRegistrationRequest.java`

```java
package com.minislack.presentation.api.user;

import org.springframework.lang.NonNull;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * ユーザー登録リクエストDTO
 */
public class UserRegistrationRequest {
    
    @NotBlank(message = "Username must not be blank")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$", 
             message = "Username must be 3-20 characters and contain only alphanumeric and underscore")
    private String username;

    @NotBlank(message = "Email must not be blank")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password must not be blank")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Display name must not be blank")
    @Size(min = 1, max = 50, message = "Display name must be 1-50 characters")
    private String displayName;

    // デフォルトコンストラクタ（JSON デシリアライズに必要）
    public UserRegistrationRequest() {
    }

    public UserRegistrationRequest(@NonNull String username, @NonNull String email, 
                                   @NonNull String password, @NonNull String displayName) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    // Getter / Setter
    @NonNull
    public String getUsername() { return username; }
    public void setUsername(@NonNull String username) { this.username = username; }

    @NonNull
    public String getEmail() { return email; }
    public void setEmail(@NonNull String email) { this.email = email; }

    @NonNull
    public String getPassword() { return password; }
    public void setPassword(@NonNull String password) { this.password = password; }

    @NonNull
    public String getDisplayName() { return displayName; }
    public void setDisplayName(@NonNull String displayName) { this.displayName = displayName; }
}
```

**学習ポイント**:
- ✅ **Jakarta Validation**: `@NotBlank`, `@Email`, `@Size`, `@Pattern`
- ✅ **カスタムメッセージ**: バリデーションエラー時のメッセージ
- ✅ **Getter/Setter**: JSONシリアライズ/デシリアライズに必要

### 3.3 ユーザー登録レスポンスDTO

**ファイル**: `src/main/java/com/minislack/presentation/api/user/UserRegistrationResponse.java`

```java
package com.minislack.presentation.api.user;

import org.springframework.lang.NonNull;

/**
 * ユーザー登録レスポンスDTO
 */
public class UserRegistrationResponse {
    private final String userId;
    private final String message;

    public UserRegistrationResponse(@NonNull String userId, @NonNull String message) {
        this.userId = userId;
        this.message = message;
    }

    @NonNull
    public String getUserId() { return userId; }
    
    @NonNull
    public String getMessage() { return message; }
}
```

### 3.4 ユーザー情報DTO

**ファイル**: `src/main/java/com/minislack/presentation/api/user/UserResponse.java`

```java
package com.minislack.presentation.api.user;

import java.time.LocalDateTime;

import org.springframework.lang.NonNull;

import com.minislack.domain.model.user.User;

/**
 * ユーザー情報レスポンスDTO
 */
public class UserResponse {
    private final String userId;
    private final String username;
    private final String email;
    private final String displayName;
    private final LocalDateTime createdAt;

    public UserResponse(@NonNull String userId, @NonNull String username, @NonNull String email,
                       @NonNull String displayName, @NonNull LocalDateTime createdAt) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.createdAt = createdAt;
    }

    @NonNull
    public static UserResponse fromDomain(@NonNull User user) {
        return new UserResponse(
            user.getUserId().getValue(),
            user.getUsername().getValue(),
            user.getEmail().getValue(),
            user.getDisplayName().getValue(),
            user.getCreatedAt()
        );
    }

    @NonNull
    public String getUserId() { return userId; }
    
    @NonNull
    public String getUsername() { return username; }
    
    @NonNull
    public String getEmail() { return email; }
    
    @NonNull
    public String getDisplayName() { return displayName; }
    
    @NonNull
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

**学習ポイント**:
- ✅ **ファクトリメソッド**: `fromDomain()`でドメインオブジェクトから生成
- ✅ **パスワード除外**: セキュリティのため含めない

---

## 4. REST APIコントローラーの実装

### 4.1 UserController

**ファイル**: `src/main/java/com/minislack/presentation/api/user/UserController.java`

```java
package com.minislack.presentation.api.user;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minislack.application.user.RegisterUserCommand;
import com.minislack.application.user.UserQueryService;
import com.minislack.application.user.UserRegistrationService;
import com.minislack.domain.exception.ResourceNotFoundException;
import com.minislack.domain.model.user.User;
import com.minislack.domain.model.user.UserId;

import jakarta.validation.Valid;

/**
 * ユーザー関連REST APIコントローラー
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    
    private final UserRegistrationService registrationService;
    private final UserQueryService queryService;

    public UserController(@NonNull UserRegistrationService registrationService,
                         @NonNull UserQueryService queryService) {
        this.registrationService = Objects.requireNonNull(registrationService);
        this.queryService = Objects.requireNonNull(queryService);
    }

    /**
     * ユーザー登録
     * POST /api/v1/users/register
     */
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

        // レスポンスDTOを作成
        UserRegistrationResponse response = new UserRegistrationResponse(
            userId.getValue(),
            "User registered successfully"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * ユーザー情報取得
     * GET /api/v1/users/{userId}
     */
    @GetMapping("/{userId}")
    @NonNull
    public ResponseEntity<UserResponse> getUserById(@PathVariable @NonNull String userId) {
        User user = queryService.findById(UserId.of(userId))
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        
        UserResponse response = UserResponse.fromDomain(user);
        return ResponseEntity.ok(response);
    }
}
```

**学習ポイント**:
- ✅ **@RestController**: REST APIコントローラー
- ✅ **@RequestMapping**: ベースパス指定
- ✅ **@PostMapping/@GetMapping**: HTTPメソッド指定
- ✅ **@Valid**: バリデーション実行
- ✅ **@RequestBody**: リクエストボディをDTOにマッピング
- ✅ **@PathVariable**: URLパスパラメータ
- ✅ **ResponseEntity**: HTTPステータスコード制御

**処理フロー**:
```text
1. HTTPリクエスト受信
2. @Valid でバリデーション
3. DTO → コマンドオブジェクト変換
4. アプリケーションサービス呼び出し
5. ドメインオブジェクト → レスポンスDTO変換
6. HTTPレスポンス返却
```

---

## 5. 認証API

### 5.1 LoginRequest

**ファイル**: `src/main/java/com/minislack/presentation/api/user/LoginRequest.java`

```java
package com.minislack.presentation.api.user;

import org.springframework.lang.NonNull;

import jakarta.validation.constraints.NotBlank;

/**
 * ログインリクエストDTO
 */
public class LoginRequest {
    
    @NotBlank(message = "Email or username must not be blank")
    private String emailOrUsername;

    @NotBlank(message = "Password must not be blank")
    private String password;

    public LoginRequest() {
    }

    public LoginRequest(@NonNull String emailOrUsername, @NonNull String password) {
        this.emailOrUsername = emailOrUsername;
        this.password = password;
    }

    @NonNull
    public String getEmailOrUsername() { return emailOrUsername; }
    public void setEmailOrUsername(@NonNull String emailOrUsername) { 
        this.emailOrUsername = emailOrUsername; 
    }

    @NonNull
    public String getPassword() { return password; }
    public void setPassword(@NonNull String password) { this.password = password; }
}
```

### 5.2 LoginResponse

**ファイル**: `src/main/java/com/minislack/presentation/api/user/LoginResponse.java`

```java
package com.minislack.presentation.api.user;

import org.springframework.lang.NonNull;

/**
 * ログインレスポンスDTO
 */
public class LoginResponse {
    private final String token;
    private final UserResponse user;

    public LoginResponse(@NonNull String token, @NonNull UserResponse user) {
        this.token = token;
        this.user = user;
    }

    @NonNull
    public String getToken() { return token; }
    
    @NonNull
    public UserResponse getUser() { return user; }
}
```

### 5.3 AuthController

**ファイル**: `src/main/java/com/minislack/presentation/api/auth/AuthController.java`

```java
package com.minislack.presentation.api.auth;

import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minislack.application.user.UserAuthenticationService;
import com.minislack.domain.model.user.User;
import com.minislack.presentation.api.user.LoginRequest;
import com.minislack.presentation.api.user.LoginResponse;
import com.minislack.presentation.api.user.UserResponse;

import jakarta.validation.Valid;

/**
 * 認証REST APIコントローラー
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    private final UserAuthenticationService authService;
    // TODO: private final JwtTokenProvider jwtTokenProvider;

    public AuthController(@NonNull UserAuthenticationService authService) {
        this.authService = Objects.requireNonNull(authService);
    }

    /**
     * ログイン
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    @NonNull
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody @NonNull LoginRequest request) {
        
        // 認証
        User user = authService.authenticate(
            request.getEmailOrUsername(),
            request.getPassword()
        );

        // TODO: JWTトークン生成（後で実装）
        String token = "dummy-jwt-token";

        // レスポンス作成
        LoginResponse response = new LoginResponse(
            token,
            UserResponse.fromDomain(user)
        );

        return ResponseEntity.ok(response);
    }
}
```

**学習ポイント**:
- ✅ **認証エンドポイント**: `/auth/login`
- ✅ **TODO**: JWT実装は後で追加

---

## 6. チャンネルAPIの実装

### 6.1 CreateChannelRequest

**ファイル**: `src/main/java/com/minislack/presentation/api/channel/CreateChannelRequest.java`

```java
package com.minislack.presentation.api.channel;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * チャンネル作成リクエストDTO
 */
public class CreateChannelRequest {
    
    @NotBlank(message = "Channel name must not be blank")
    @Size(min = 4, max = 50, message = "Channel name must be 4-50 characters")
    private String channelName;

    @Size(max = 500, message = "Description must be 500 characters or less")
    private String description;

    private boolean isPublic = true;

    public CreateChannelRequest() {
    }

    @NonNull
    public String getChannelName() { return channelName; }
    public void setChannelName(@NonNull String channelName) { this.channelName = channelName; }

    @Nullable
    public String getDescription() { return description; }
    public void setDescription(@Nullable String description) { this.description = description; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
}
```

### 6.2 ChannelResponse

**ファイル**: `src/main/java/com/minislack/presentation/api/channel/ChannelResponse.java`

```java
package com.minislack.presentation.api.channel;

import java.time.LocalDateTime;

import org.springframework.lang.NonNull;

import com.minislack.domain.model.channel.Channel;

/**
 * チャンネル情報レスポンスDTO
 */
public class ChannelResponse {
    private final String channelId;
    private final String channelName;
    private final String description;
    private final boolean isPublic;
    private final String createdBy;
    private final LocalDateTime createdAt;

    public ChannelResponse(@NonNull String channelId, @NonNull String channelName, 
                          @NonNull String description, boolean isPublic, 
                          @NonNull String createdBy, @NonNull LocalDateTime createdAt) {
        this.channelId = channelId;
        this.channelName = channelName;
        this.description = description;
        this.isPublic = isPublic;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    @NonNull
    public static ChannelResponse fromDomain(@NonNull Channel channel) {
        return new ChannelResponse(
            channel.getChannelId().getValue(),
            channel.getChannelName().getValue(),
            channel.getDescription().getValue(),
            channel.isPublic(),
            channel.getCreatedBy().getValue(),
            channel.getCreatedAt()
        );
    }

    @NonNull
    public String getChannelId() { return channelId; }
    @NonNull
    public String getChannelName() { return channelName; }
    @NonNull
    public String getDescription() { return description; }
    public boolean isPublic() { return isPublic; }
    @NonNull
    public String getCreatedBy() { return createdBy; }
    @NonNull
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

### 6.3 ChannelController

**ファイル**: `src/main/java/com/minislack/presentation/api/channel/ChannelController.java`

```java
package com.minislack.presentation.api.channel;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minislack.application.channel.ChannelManagementService;
import com.minislack.application.channel.CreateChannelCommand;
import com.minislack.domain.model.channel.Channel;
import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.user.UserId;

import jakarta.validation.Valid;

/**
 * チャンネル関連REST APIコントローラー
 */
@RestController
@RequestMapping("/api/v1/channels")
public class ChannelController {
    
    private final ChannelManagementService channelService;

    public ChannelController(@NonNull ChannelManagementService channelService) {
        this.channelService = Objects.requireNonNull(channelService);
    }

    /**
     * チャンネル作成
     * POST /api/v1/channels
     */
    @PostMapping
    @NonNull
    public ResponseEntity<ChannelResponse> createChannel(
            @Valid @RequestBody @NonNull CreateChannelRequest request,
            @RequestHeader("X-User-Id") @NonNull String currentUserId) {
        
        CreateChannelCommand command = new CreateChannelCommand(
            request.getChannelName(),
            request.getDescription(),
            request.isPublic(),
            currentUserId
        );

        ChannelId channelId = channelService.createChannel(command);

        // TODO: 作成したチャンネルを取得して返却
        ChannelResponse response = new ChannelResponse(
            channelId.getValue(),
            request.getChannelName(),
            request.getDescription() != null ? request.getDescription() : "",
            request.isPublic(),
            currentUserId,
            java.time.LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 公開チャンネル一覧取得
     * GET /api/v1/channels
     */
    @GetMapping
    @NonNull
    public ResponseEntity<List<ChannelResponse>> getPublicChannels() {
        List<Channel> channels = channelService.findPublicChannels();
        
        List<ChannelResponse> response = channels.stream()
            .map(ChannelResponse::fromDomain)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    /**
     * チャンネル参加
     * POST /api/v1/channels/{channelId}/join
     */
    @PostMapping("/{channelId}/join")
    @NonNull
    public ResponseEntity<Void> joinChannel(
            @PathVariable @NonNull String channelId,
            @RequestHeader("X-User-Id") @NonNull String currentUserId) {
        
        channelService.joinChannel(
            UserId.of(currentUserId),
            ChannelId.of(channelId)
        );
        
        return ResponseEntity.ok().build();
    }

    /**
     * チャンネル退出
     * POST /api/v1/channels/{channelId}/leave
     */
    @PostMapping("/{channelId}/leave")
    @NonNull
    public ResponseEntity<Void> leaveChannel(
            @PathVariable @NonNull String channelId,
            @RequestHeader("X-User-Id") @NonNull String currentUserId) {
        
        channelService.leaveChannel(
            UserId.of(currentUserId),
            ChannelId.of(channelId)
        );
        
        return ResponseEntity.ok().build();
    }
}
```

**学習ポイント**:
- ✅ **@RequestHeader**: 認証情報の取得（簡易版、後でJWT実装）
- ✅ **@PathVariable**: URLパラメータ
- ✅ **Stream API**: リスト変換
- ✅ **ResponseEntity.ok().build()**: レスポンスボディなし（204 No Content相当）

---

## 7. メッセージAPIの実装

### 7.1 SendMessageRequest

**ファイル**: `src/main/java/com/minislack/presentation/api/message/SendMessageRequest.java`

```java
package com.minislack.presentation.api.message;

import org.springframework.lang.NonNull;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * メッセージ送信リクエストDTO
 */
public class SendMessageRequest {
    
    @NotBlank(message = "Content must not be blank")
    @Size(min = 1, max = 2000, message = "Content must be 1-2000 characters")
    private String content;

    public SendMessageRequest() {
    }

    public SendMessageRequest(@NonNull String content) {
        this.content = content;
    }

    @NonNull
    public String getContent() { return content; }
    public void setContent(@NonNull String content) { this.content = content; }
}
```

### 7.2 MessageResponse

**ファイル**: `src/main/java/com/minislack/presentation/api/message/MessageResponse.java`

```java
package com.minislack.presentation.api.message;

import java.time.LocalDateTime;

import org.springframework.lang.NonNull;

import com.minislack.domain.model.message.Message;

/**
 * メッセージレスポンスDTO
 */
public class MessageResponse {
    private final String messageId;
    private final String channelId;
    private final String userId;
    private final String content;
    private final LocalDateTime createdAt;

    public MessageResponse(@NonNull String messageId, @NonNull String channelId, 
                          @NonNull String userId, @NonNull String content, 
                          @NonNull LocalDateTime createdAt) {
        this.messageId = messageId;
        this.channelId = channelId;
        this.userId = userId;
        this.content = content;
        this.createdAt = createdAt;
    }

    @NonNull
    public static MessageResponse fromDomain(@NonNull Message message) {
        return new MessageResponse(
            message.getMessageId().getValue(),
            message.getChannelId().getValue(),
            message.getUserId().getValue(),
            message.getContent().getValue(),
            message.getCreatedAt()
        );
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
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

### 7.3 MessageController

**ファイル**: `src/main/java/com/minislack/presentation/api/message/MessageController.java`

```java
package com.minislack.presentation.api.message;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minislack.application.message.MessageService;
import com.minislack.application.message.SendMessageCommand;
import com.minislack.domain.model.channel.ChannelId;
import com.minislack.domain.model.message.Message;
import com.minislack.domain.model.message.MessageId;
import com.minislack.domain.model.user.UserId;

import jakarta.validation.Valid;

/**
 * メッセージ関連REST APIコントローラー
 */
@RestController
@RequestMapping("/api/v1/channels/{channelId}/messages")
public class MessageController {
    
    private final MessageService messageService;

    public MessageController(@NonNull MessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService);
    }

    /**
     * メッセージ送信
     * POST /api/v1/channels/{channelId}/messages
     */
    @PostMapping
    @NonNull
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable @NonNull String channelId,
            @Valid @RequestBody @NonNull SendMessageRequest request,
            @RequestHeader("X-User-Id") @NonNull String currentUserId) {
        
        SendMessageCommand command = new SendMessageCommand(
            channelId,
            request.getContent()
        );

        MessageId messageId = messageService.sendMessage(
            command,
            UserId.of(currentUserId)
        );

        // TODO: 送信したメッセージを取得して返却
        MessageResponse response = new MessageResponse(
            messageId.getValue(),
            channelId,
            currentUserId,
            request.getContent(),
            java.time.LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * メッセージ一覧取得
     * GET /api/v1/channels/{channelId}/messages
     */
    @GetMapping
    @NonNull
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable @NonNull String channelId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestHeader("X-User-Id") @NonNull String currentUserId) {
        
        List<Message> messages = messageService.getMessages(
            ChannelId.of(channelId),
            UserId.of(currentUserId),
            limit,
            offset
        );

        List<MessageResponse> response = messages.stream()
            .map(MessageResponse::fromDomain)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}
```

**学習ポイント**:
- ✅ **@PathVariable**: チャンネルIDをパスから取得
- ✅ **@RequestParam**: クエリパラメータ（limit, offset）
- ✅ **defaultValue**: パラメータのデフォルト値

---

## 8. グローバル例外ハンドラー

### 8.1 ErrorResponse / ValidationErrorResponse

これらは既に`docs/05-exception-handling.md`で定義済みです。

**ファイル**: `src/main/java/com/minislack/presentation/api/dto/ErrorResponse.java`
**ファイル**: `src/main/java/com/minislack/presentation/api/dto/ValidationErrorResponse.java`

### 8.2 GlobalExceptionHandler

**ファイル**: `src/main/java/com/minislack/presentation/api/exception/GlobalExceptionHandler.java`

詳細は`docs/05-exception-handling.md`を参照してください。

---

## 9. CORS設定

### 9.1 CorsConfig

**ファイル**: `src/main/java/com/minislack/presentation/config/CorsConfig.java`

```java
package com.minislack.presentation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS設定
 */
@Configuration
public class CorsConfig {

    @Bean
    @NonNull
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // 開発環境用（本番では制限する）
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
```

**学習ポイント**:
- ✅ **@Configuration**: Spring設定クラス
- ✅ **CORS**: Cross-Origin Resource Sharing
- ✅ **本番注意**: `addAllowedOriginPattern("*")`は開発用のみ

---

## 10. API動作確認

### 10.1 curlでのテスト

**ユーザー登録**:
```bash
curl -X POST http://localhost:8080/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123",
    "displayName": "Test User"
  }'
```

**期待されるレスポンス**:
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "User registered successfully"
}
```

**ログイン**:
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "emailOrUsername": "test@example.com",
    "password": "password123"
  }'
```

**チャンネル作成**:
```bash
curl -X POST http://localhost:8080/api/v1/channels \
  -H "Content-Type: application/json" \
  -H "X-User-Id: <user-id>" \
  -d '{
    "channelName": "general",
    "description": "General discussion",
    "isPublic": true
  }'
```

**メッセージ送信**:
```bash
curl -X POST http://localhost:8080/api/v1/channels/<channel-id>/messages \
  -H "Content-Type: application/json" \
  -H "X-User-Id: <user-id>" \
  -d '{
    "content": "Hello, MiniSlack!"
  }'
```

---

## 11. まとめ

### 11.1 プレゼンテーション層実装の要点

1. **DTO**:
   - API入出力の表現
   - Jakarta Validationでバリデーション
   - ファクトリメソッド（`fromDomain()`）

2. **コントローラー**:
   - `@RestController`でREST API
   - DTO → コマンド → ドメイン → レスポンスDTOの流れ
   - HTTPステータスコードの適切な使用

3. **例外ハンドリング**:
   - `@RestControllerAdvice`でグローバル処理
   - 例外をHTTPレスポンスに変換

4. **CORS**:
   - フロントエンドとの連携に必要

### 11.2 次のステップ

プレゼンテーション層の実装が完了しました！次は統合して実際に動かします：

- [10-user-management.md](10-user-management.md) - ユーザー管理機能の実装と動作確認

---

## 12. 参考資料

- [Spring Web MVC](https://docs.spring.io/spring-framework/reference/web/webmvc.html)
- [Jakarta Validation](https://beanvalidation.org/)
- [REST API Best Practices](https://restfulapi.net/)

