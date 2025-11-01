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

**理由**:
- Checked Exceptionは上位レイヤーに依存を強制する
- オニオンアーキテクチャの依存性ルールを守るため
- Spring Bootのトランザクション管理との相性が良い

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
