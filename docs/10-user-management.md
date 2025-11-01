# ユーザー管理機能実装ハンズオン

## 1. はじめに

このドキュメントでは、ユーザー管理機能を**実際に動くコード**として実装し、動作確認まで行います。

### 1.1 実装する機能

- ユーザー登録API
- ログインAPI
- ユーザー情報取得API

### 1.2 実装の流れ

オニオンアーキテクチャに従って、**内側から外側**へ実装します：

```text
1. ドメイン層（User, UserId等）
2. アプリケーション層（UserRegistrationService等）
3. インフラ層（UserRepositoryImpl等）
4. プレゼンテーション層（UserController等）
5. 動作確認
```

---

## 2. Step 1: ドメイン層の実装

### 2.1 ディレクトリ作成

```bash
mkdir -p src/main/java/com/minislack/domain/model/user
mkdir -p src/main/java/com/minislack/domain/exception
```

### 2.2 値オブジェクトの作成

前のドキュメント（`06-domain-layer.md`）を参照して、以下のファイルを作成してください：

1. `UserId.java`
2. `Username.java`
3. `Email.java`
4. `Password.java`
5. `DisplayName.java`
6. `IPasswordEncoder.java`

### 2.3 Userエンティティの作成

`User.java`と`IUserRepository.java`を作成してください。

### 2.4 ドメイン例外の作成

```bash
mkdir -p src/main/java/com/minislack/domain/exception
```

1. `DomainException.java`
2. `ResourceNotFoundException.java`
3. `DuplicateResourceException.java`
4. `BusinessRuleViolationException.java`

### 2.5 コンパイル確認

```bash
./gradlew compileJava
```

エラーがなければ成功です！

---

## 3. Step 2: アプリケーション層の実装

### 3.1 ディレクトリ作成

```bash
mkdir -p src/main/java/com/minislack/application/user
mkdir -p src/main/java/com/minislack/application/exception
```

### 3.2 コマンドオブジェクトの作成

**ファイル**: `src/main/java/com/minislack/application/user/RegisterUserCommand.java`

（`07-application-layer.md`を参照）

### 3.3 アプリケーションサービスの作成

**ファイル**: `src/main/java/com/minislack/application/user/UserRegistrationService.java`

**ファイル**: `src/main/java/com/minislack/application/user/UserAuthenticationService.java`

**ファイル**: `src/main/java/com/minislack/application/user/UserQueryService.java`

### 3.4 アプリケーション例外の作成

**ファイル**: `src/main/java/com/minislack/application/exception/ApplicationException.java`

**ファイル**: `src/main/java/com/minislack/application/exception/AuthenticationException.java`

**ファイル**: `src/main/java/com/minislack/application/exception/AuthorizationException.java`

---

## 4. Step 3: インフラ層の実装

### 4.1 JPAエンティティの作成

```bash
mkdir -p src/main/java/com/minislack/infrastructure/persistence/user
```

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/user/UserJpaEntity.java`

（`08-infrastructure-layer.md`を参照）

### 4.2 マッパーの作成

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/user/UserEntityMapper.java`

### 4.3 Spring Data JPA Repositoryの作成

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/user/SpringDataUserRepository.java`

### 4.4 リポジトリ実装の作成

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/user/UserRepositoryImpl.java`

### 4.5 パスワードエンコーダーの作成

```bash
mkdir -p src/main/java/com/minislack/infrastructure/security
```

**ファイル**: `src/main/java/com/minislack/infrastructure/security/BCryptPasswordEncoderAdapter.java`

### 4.6 セキュリティ設定

**ファイル**: `src/main/java/com/minislack/infrastructure/security/SecurityConfig.java`

```java
package com.minislack.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security設定
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @NonNull
    public SecurityFilterChain filterChain(@NonNull HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // 開発用（本番では有効化）
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/users/register", "/api/v1/auth/login").permitAll()
                .requestMatchers("/api/**").permitAll() // 一旦すべて許可（後でJWT実装）
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            );
        
        return http.build();
    }
}
```

**学習ポイント**:
- ✅ **CSRF無効化**: 開発用（REST APIでは通常無効）
- ✅ **permitAll()**: 認証不要のエンドポイント
- ✅ **JWT実装は後で**: 今回は簡易版

**⚠️ 本番環境への移行時の必須チェックリスト**:
- [ ] CSRF保護を有効化（Web UIがある場合）
- [ ] CORSを制限設定（特定のオリジンのみ許可）
- [ ] `/api/**`ではなく、具体的なパスのみ許可に変更
- [ ] JWT認証の完全実装
- [ ] Actuatorエンドポイントの保護（管理者のみアクセス可能に）
- [ ] HTTPSの設定

**本番環境用の設定例**:
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/users/register", "/api/v1/auth/login").permitAll()
            .requestMatchers("/actuator/health").permitAll()
            .requestMatchers("/actuator/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
    
    return http.build();
}
```

---

## 5. Step 4: プレゼンテーション層の実装

### 5.1 DTOの作成

```bash
mkdir -p src/main/java/com/minislack/presentation/api/user
mkdir -p src/main/java/com/minislack/presentation/api/auth
mkdir -p src/main/java/com/minislack/presentation/api/dto
mkdir -p src/main/java/com/minislack/presentation/api/exception
```

以下のファイルを作成してください（`09-presentation-layer.md`を参照）：

1. `UserRegistrationRequest.java`
2. `UserRegistrationResponse.java`
3. `UserResponse.java`
4. `LoginRequest.java`
5. `LoginResponse.java`

### 5.2 例外ハンドラーの作成

**ファイル**: `src/main/java/com/minislack/presentation/api/dto/ErrorResponse.java`

**ファイル**: `src/main/java/com/minislack/presentation/api/dto/ValidationErrorResponse.java`

**ファイル**: `src/main/java/com/minislack/presentation/api/exception/GlobalExceptionHandler.java`

（`05-exception-handling.md`を参照）

### 5.3 コントローラーの作成

**ファイル**: `src/main/java/com/minislack/presentation/api/user/UserController.java`

**ファイル**: `src/main/java/com/minislack/presentation/api/auth/AuthController.java`

---

## 6. Step 5: アプリケーション起動

### 6.1 Dockerコンテナ起動

```bash
docker-compose up -d
```

確認：
```bash
docker-compose ps
```

### 6.2 アプリケーション起動

```bash
./gradlew bootRun
```

起動ログで以下を確認：
- `Tomcat started on port 8080`
- `Started MinislackApplication`

---

## 7. Step 6: 動作確認

### 7.1 ユーザー登録テスト

**リクエスト**:
```bash
curl -X POST http://localhost:8080/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "password123",
    "displayName": "Alice"
  }' | jq
```

**期待されるレスポンス**:
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "User registered successfully"
}
```

### 7.2 重複登録テスト（エラーケース）

```bash
# 同じメールアドレスで再度登録
curl -X POST http://localhost:8080/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice2",
    "email": "alice@example.com",
    "password": "password123",
    "displayName": "Alice2"
  }' | jq
```

**期待されるレスポンス**（409 Conflict）:
```json
{
  "status": 409,
  "error": "Duplicate Resource",
  "message": "Email already exists: alice@example.com",
  "timestamp": "2025-11-01T12:34:56"
}
```

### 7.3 バリデーションエラーテスト

```bash
# 短すぎるユーザー名
curl -X POST http://localhost:8080/api/v1/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "ab",
    "email": "test@example.com",
    "password": "password123",
    "displayName": "Test"
  }' | jq
```

**期待されるレスポンス**（400 Bad Request）:
```json
{
  "status": 400,
  "error": "Validation Failed",
  "fieldErrors": {
    "username": "Username must be 3-20 characters and contain only alphanumeric and underscore"
  },
  "timestamp": "2025-11-01T12:34:56"
}
```

### 7.4 ログインテスト

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "emailOrUsername": "alice@example.com",
    "password": "password123"
  }' | jq
```

**期待されるレスポンス**:
```json
{
  "token": "dummy-jwt-token",
  "user": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "username": "alice",
    "email": "alice@example.com",
    "displayName": "Alice",
    "createdAt": "2025-11-01T12:30:00"
  }
}
```

### 7.5 ユーザー情報取得テスト

```bash
# userIdは登録時に返されたIDを使用
curl -X GET http://localhost:8080/api/v1/users/<user-id> | jq
```

---

## 8. データベース確認

### 8.1 PostgreSQLに接続

```bash
docker exec -it minislack-postgres psql -U minislack -d minislack
```

### 8.2 テーブル確認

```sql
-- テーブル一覧
\dt

-- usersテーブルのスキーマ確認
\d users

-- データ確認
SELECT user_id, username, email, display_name, created_at FROM users;
```

### 8.3 登録したユーザーの確認

```sql
SELECT 
    user_id, 
    username, 
    email, 
    display_name,
    created_at
FROM users
WHERE username = 'alice';
```

---

## 9. トラブルシューティング

### 9.1 アプリケーションが起動しない

**エラー**: `Could not resolve placeholder 'JWT_SECRET'`

**解決**:
`application.yml`で`:${JWT_SECRET:default-value}`のようにデフォルト値を設定

### 9.2 PostgreSQLに接続できない

**エラー**: `Connection refused`

**確認**:
```bash
docker-compose ps
docker-compose logs postgres
```

**解決**:
```bash
docker-compose restart postgres
```

### 9.3 バリデーションエラーが返らない

**原因**: `@Valid`アノテーションを忘れている

**確認**:
```java
public ResponseEntity<UserRegistrationResponse> register(
    @Valid @RequestBody UserRegistrationRequest request) { // @Validが必要
    // ...
}
```

### 9.4 例外がHTTPレスポンスに変換されない

**原因**: `GlobalExceptionHandler`がSpringに認識されていない

**確認**:
- `@RestControllerAdvice`アノテーションがあるか
- パッケージが`com.minislack`以下にあるか（コンポーネントスキャン対象）

---

## 10. Postmanでのテスト

### 10.1 Postmanコレクション作成

1. Postmanを起動
2. 新しいコレクション「MiniSlack」を作成
3. 以下のリクエストを追加：

#### ユーザー登録

```
POST http://localhost:8080/api/v1/users/register
Content-Type: application/json

{
  "username": "bob",
  "email": "bob@example.com",
  "password": "password123",
  "displayName": "Bob"
}
```

#### ログイン

```
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "emailOrUsername": "bob@example.com",
  "password": "password123"
}
```

#### ユーザー情報取得

```
GET http://localhost:8080/api/v1/users/{{userId}}
```

---

## 11. 単体テストの実装

### 11.1 ドメイン層のテスト

**ファイル**: `src/test/java/com/minislack/domain/model/user/UsernameTest.java`

（`06-domain-layer.md`を参照）

### 11.2 アプリケーション層のテスト

**ファイル**: `src/test/java/com/minislack/application/user/UserRegistrationServiceTest.java`

（`07-application-layer.md`を参照）

### 11.3 統合テスト

**ファイル**: `src/test/java/com/minislack/presentation/api/user/UserControllerIntegrationTest.java`

```java
package com.minislack.presentation.api.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ユーザーコントローラー統合テスト
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void register_ValidRequest_Returns201Created() throws Exception {
        String requestBody = """
            {
              "username": "testuser",
              "email": "test@example.com",
              "password": "password123",
              "displayName": "Test User"
            }
            """;

        mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").exists())
            .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

    @Test
    void register_InvalidEmail_Returns400BadRequest() throws Exception {
        String requestBody = """
            {
              "username": "testuser",
              "email": "invalid-email",
              "password": "password123",
              "displayName": "Test User"
            }
            """;

        mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Validation Failed"));
    }
}
```

**学習ポイント**:
- ✅ **@SpringBootTest**: Spring Bootコンテキスト全体をロード
- ✅ **@AutoConfigureMockMvc**: MockMvcを自動設定
- ✅ **@ActiveProfiles("test")**: テスト用設定を使用
- ✅ **Text Blocks**: Java 15+の複数行文字列

### 11.4 テスト実行

```bash
./gradlew test
```

---

## 12. ログ確認

### 12.1 アプリケーションログ

ユーザー登録時のログ出力例：

```
2025-11-01 12:34:56 - Hibernate: insert into users ...
2025-11-01 12:34:56 - User registered successfully: UserId{550e8400-...}
```

### 12.2 SQL ログ

`application.yml`で`spring.jpa.show-sql: true`を設定しているため、
実行されるSQLが表示されます：

```sql
Hibernate: 
    insert 
    into
        users
        (created_at, display_name, email, password_hash, updated_at, username, user_id) 
    values
        (?, ?, ?, ?, ?, ?, ?)
```

---

## 13. まとめ

### 13.1 実装したもの

- ✅ ドメイン層: User, UserId, Username, Email, Password, DisplayName
- ✅ アプリケーション層: UserRegistrationService, UserAuthenticationService
- ✅ インフラ層: UserRepositoryImpl, BCryptPasswordEncoderAdapter
- ✅ プレゼンテーション層: UserController, AuthController
- ✅ 例外処理: GlobalExceptionHandler
- ✅ セキュリティ設定: SecurityConfig

### 13.2 動作確認した機能

- ✅ ユーザー登録API（正常系・異常系）
- ✅ ログインAPI
- ✅ ユーザー情報取得API
- ✅ バリデーションエラー処理
- ✅ 重複チェック
- ✅ データベース永続化

### 13.3 次のステップ

ユーザー管理機能が完成しました！次はチャンネル管理機能を実装します：

- [11-channel-management.md](11-channel-management.md) - チャンネル管理機能実装

---

## 14. 参考コマンド集

```bash
# アプリケーション起動
./gradlew bootRun

# テスト実行
./gradlew test

# Dockerコンテナ起動
docker-compose up -d

# PostgreSQL接続
docker exec -it minislack-postgres psql -U minislack -d minislack

# ログ確認
docker-compose logs -f postgres

# アプリケーション再起動
# Ctrl+C で停止後
./gradlew bootRun
```

---

## 15. よくある質問

### Q1. なぜバリデーションをDTOとドメインの両方で行うのか？

**A**: 役割が違います：
- **DTOバリデーション（Jakarta Validation）**: HTTP入力の形式チェック
- **ドメインバリデーション（値オブジェクト）**: ビジネスルールの保証

DTOバリデーションに通っても、ドメインバリデーションで弾かれることもあります。

### Q2. パスワードはどこでハッシュ化されるのか？

**A**: 
1. Presentation層: 生パスワードをそのまま渡す
2. Application層: `Password.fromRawPassword()`でハッシュ化
3. Infrastructure層: ハッシュ化済みパスワードを保存

### Q3. なぜUserIdをUUIDにしたのか？

**A**: 
- 分散システムで一意性保証
- 自動採番（DBに依存しない）
- セキュリティ（推測困難）

### Q4. JWTはいつ実装するのか？

**A**: 次のフェーズで実装します。現在は`X-User-Id`ヘッダーで簡易認証しています。

---

## 16. 参考資料

- [Spring Boot Testing](https://spring.io/guides/gs/testing-web)
- [MockMvc](https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-framework.html)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)

