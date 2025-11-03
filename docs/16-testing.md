# テスト実装ハンズオン

## 1. はじめに

このドキュメントでは、MiniSlackの包括的なテスト戦略とテスト実装を説明します。

### 1.1 テストの重要性

**なぜテストを書くのか？**
- コードの品質保証
- リファクタリングの安全性
- ドキュメントとしての役割
- バグの早期発見

### 1.2 テストの種類

| テストレベル | 目的 | 範囲 | 速度 |
|------------|------|------|------|
| **単体テスト** | 個別クラスの動作確認 | 1クラス | 高速 |
| **統合テスト** | 複数コンポーネントの連携確認 | 複数クラス | 中速 |
| **E2Eテスト** | システム全体の動作確認 | 全体 | 低速 |

### 1.3 テストピラミッド

```text
        /\
       /E2E\      少数（遅い、高コスト）
      /------\
     / 統合   \    中程度
    /----------\
   /   単体     \  多数（速い、低コスト）
  /--------------\
```

**推奨比率**: 単体70%、統合20%、E2E10%

---

## 2. 単体テスト（Unit Test）

### 2.1 ドメイン層のテスト

ドメイン層は依存がないため、最もテストしやすいです。

#### 2.1.1 値オブジェクトのテスト

**ファイル**: `src/test/java/com/minislack/domain/model/user/EmailTest.java`

```java
package com.minislack.domain.model.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Email値オブジェクトのテスト
 */
class EmailTest {

    @Test
    void constructor_ValidEmail_Success() {
        // Given
        String validEmail = "test@example.com";
        
        // When
        Email email = new Email(validEmail);
        
        // Then
        assertEquals("test@example.com", email.getValue());
    }

    @Test
    void constructor_EmailInUpperCase_ConvertsToLowerCase() {
        // Given
        String upperCaseEmail = "Test@EXAMPLE.COM";
        
        // When
        Email email = new Email(upperCaseEmail);
        
        // Then
        assertEquals("test@example.com", email.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "invalid", "test@", "@example.com", "test@.com"})
    void constructor_InvalidEmail_ThrowsException(String invalidEmail) {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            new Email(invalidEmail);
        });
    }

    @Test
    void equals_SameValue_ReturnsTrue() {
        // Given
        Email email1 = new Email("test@example.com");
        Email email2 = new Email("TEST@EXAMPLE.COM"); // 大文字小文字は無視
        
        // When & Then
        assertEquals(email1, email2);
    }

    @Test
    void equals_DifferentValue_ReturnsFalse() {
        // Given
        Email email1 = new Email("test1@example.com");
        Email email2 = new Email("test2@example.com");
        
        // When & Then
        assertNotEquals(email1, email2);
    }

    @Test
    void hashCode_SameValue_ReturnsSameHashCode() {
        // Given
        Email email1 = new Email("test@example.com");
        Email email2 = new Email("test@example.com");
        
        // When & Then
        assertEquals(email1.hashCode(), email2.hashCode());
    }
}
```

**学習ポイント**:
- ✅ **@ParameterizedTest**: 複数の入力値でテスト
- ✅ **@ValueSource**: パラメータの値を指定
- ✅ **Given-When-Then**: テストの構造化
- ✅ **assertThrows**: 例外のテスト

**@ParameterizedTestの利点**:

パラメータ化テストを使用することで、以下のメリットがあります：

1. **テストコードの削減**: 6個の無効パターンを1メソッドでカバー（個別に書くと6メソッド必要）
2. **テストレポートの可読性**: 各パターンが個別に表示される
   ```
   ✓ constructor_InvalidEmail_ThrowsException(String) [1] ""
   ✓ constructor_InvalidEmail_ThrowsException(String) [2] " "
   ✓ constructor_InvalidEmail_ThrowsException(String) [3] "invalid"
   ...
   ```
3. **拡張の容易性**: 新しい無効パターンを`@ValueSource`に追加するだけ

**従来の方法との比較**:
```java
// ❌ 冗長な方法（6個のテストメソッド）
@Test void test_EmptyString() { ... }
@Test void test_BlankString() { ... }
@Test void test_NoAtSign() { ... }
// ...

// ✅ パラメータ化テスト（1個のテストメソッド）
@ParameterizedTest
@ValueSource(strings = {"", " ", "invalid", ...})
void test_InvalidEmails(String invalidEmail) { ... }
```

#### 2.1.2 エンティティのテスト

**ファイル**: `src/test/java/com/minislack/domain/model/user/UserTest.java`

```java
package com.minislack.domain.model.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Userエンティティのテスト
 */
class UserTest {
    
    private IPasswordEncoder mockEncoder;

    @BeforeEach
    void setUp() {
        mockEncoder = mock(IPasswordEncoder.class);
        when(mockEncoder.encode(anyString())).thenReturn("hashed_password");
        when(mockEncoder.matches("correct_password", "hashed_password")).thenReturn(true);
        when(mockEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);
    }

    @Test
    void changePassword_CorrectCurrentPassword_Success() {
        // Given
        User user = createTestUser();
        Password currentPassword = Password.fromHashedValue("hashed_password");
        Password newPassword = Password.fromRawPassword("new_password123", mockEncoder);
        
        // When
        user.changePassword(currentPassword, newPassword, mockEncoder);
        
        // Then
        assertEquals(newPassword, user.getPassword());
    }

    @Test
    void changePassword_IncorrectCurrentPassword_ThrowsException() {
        // Given
        User user = createTestUser();
        Password wrongPassword = Password.fromRawPassword("wrong_password", mockEncoder);
        Password newPassword = Password.fromRawPassword("new_password123", mockEncoder);
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            user.changePassword(wrongPassword, newPassword, mockEncoder);
        });
    }

    @Test
    void updateProfile_ValidDisplayName_Success() {
        // Given
        User user = createTestUser();
        DisplayName newDisplayName = new DisplayName("New Name");
        
        // When
        user.updateProfile(newDisplayName);
        
        // Then
        assertEquals(newDisplayName, user.getDisplayName());
    }

    private User createTestUser() {
        return new User(
            UserId.newId(),
            new Username("testuser"),
            new Email("test@example.com"),
            Password.fromRawPassword("password123", mockEncoder),
            new DisplayName("Test User")
        );
    }
}
```

**学習ポイント**:
- ✅ **Mockito**: `mock()`, `when()`, `thenReturn()`
- ✅ **@BeforeEach**: テスト前の共通処理
- ✅ **テストヘルパーメソッド**: `createTestUser()`

**発展：mock検証の強化**:

現在の実装は基本的なモック使用方法を示しています。より厳密な検証も可能です：

```java
// 基本的なモック（現在の実装）
when(mockEncoder.encode(anyString())).thenReturn("hashed_password");

// より厳密な検証（応用編）
when(mockEncoder.encode("correct_password")).thenReturn("specific_hash");

// さらに verify() で呼び出し確認
@Test
void changePassword_CorrectCurrentPassword_Success() {
    User user = createTestUser();
    Password newPassword = Password.fromRawPassword("new_password123", mockEncoder);
    
    user.changePassword(currentPassword, newPassword, mockEncoder);
    
    // メソッドが呼ばれたことを検証
    verify(mockEncoder).encode("new_password123");
    verify(mockEncoder).matches(anyString(), anyString());
}
```

このような発展的なテクニックにより、テスト品質がさらに向上します。

---

## 3. 統合テスト（Integration Test）

### 3.1 アプリケーション層のテスト

#### 3.1.1 UserRegistrationServiceのテスト

**ファイル**: `src/test/java/com/minislack/application/user/UserRegistrationServiceIntegrationTest.java`

```java
package com.minislack.application.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.minislack.domain.exception.DuplicateResourceException;
import com.minislack.domain.model.user.Email;
import com.minislack.domain.model.user.IUserRepository;
import com.minislack.domain.model.user.User;
import com.minislack.domain.model.user.UserId;

/**
 * UserRegistrationService統合テスト
 * 実際のSpringコンテキストを使用
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRegistrationServiceIntegrationTest {

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private IUserRepository userRepository;

    @Test
    void registerUser_ValidCommand_SavesUserToDatabase() {
        // Given
        RegisterUserCommand command = new RegisterUserCommand(
            "integrationtest",
            "integration@example.com",
            "password123",
            "Integration Test User"
        );

        // When
        UserId userId = registrationService.registerUser(command);

        // Then
        assertNotNull(userId);
        
        User savedUser = userRepository.findById(userId).orElseThrow();
        assertEquals("integrationtest", savedUser.getUsername().getValue());
        assertEquals("integration@example.com", savedUser.getEmail().getValue());
    }

    @Test
    void registerUser_DuplicateEmail_ThrowsException() {
        // Given
        RegisterUserCommand command1 = new RegisterUserCommand(
            "user1", "test@example.com", "password123", "User 1"
        );
        registrationService.registerUser(command1);
        
        RegisterUserCommand command2 = new RegisterUserCommand(
            "user2", "test@example.com", "password456", "User 2"
        );

        // When & Then
        DuplicateResourceException exception = assertThrows(
            DuplicateResourceException.class,
            () -> registrationService.registerUser(command2)
        );
        
        assertEquals("Email already exists: test@example.com", exception.getMessage());
    }
}
```

**学習ポイント**:
- ✅ **@SpringBootTest**: 完全なSpringコンテキスト
- ✅ **@ActiveProfiles("test")**: テスト用設定（H2 Database）
- ✅ **@Transactional**: テスト後に自動ロールバック
- ✅ **@Autowired**: 実際のBeanを注入

---

### 3.2 リポジトリ層のテスト

#### 3.2.1 UserRepositoryのテスト

**ファイル**: `src/test/java/com/minislack/infrastructure/persistence/user/UserRepositoryImplTest.java`

```java
package com.minislack.infrastructure.persistence.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.minislack.domain.model.user.DisplayName;
import com.minislack.domain.model.user.Email;
import com.minislack.domain.model.user.IUserRepository;
import com.minislack.domain.model.user.Password;
import com.minislack.domain.model.user.User;
import com.minislack.domain.model.user.UserId;
import com.minislack.domain.model.user.Username;

/**
 * UserRepository統合テスト
 * JPA機能のテスト
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({UserRepositoryImpl.class, UserEntityMapper.class})
class UserRepositoryImplTest {

    @Autowired
    private IUserRepository userRepository;

    @Test
    void save_ValidUser_ReturnsUserWithId() {
        // Given
        User user = new User(
            UserId.newId(),
            new Username("repotest"),
            new Email("repo@example.com"),
            Password.fromHashedValue("hashed_password"),
            new DisplayName("Repo Test")
        );

        // When
        User savedUser = userRepository.save(user);

        // Then
        assertEquals(user.getUserId(), savedUser.getUserId());
        assertEquals("repotest", savedUser.getUsername().getValue());
    }

    @Test
    void findByEmail_ExistingEmail_ReturnsUser() {
        // Given
        User user = createAndSaveUser("test@example.com");
        Email email = new Email("test@example.com");

        // When
        Optional<User> found = userRepository.findByEmail(email);

        // Then
        assertTrue(found.isPresent());
        assertEquals(user.getUserId(), found.get().getUserId());
    }

    @Test
    void existsByEmail_ExistingEmail_ReturnsTrue() {
        // Given
        createAndSaveUser("exists@example.com");
        Email email = new Email("exists@example.com");

        // When
        boolean exists = userRepository.existsByEmail(email);

        // Then
        assertTrue(exists);
    }

    private User createAndSaveUser(String email) {
        User user = new User(
            UserId.newId(),
            new Username("testuser"),
            new Email(email),
            Password.fromHashedValue("hashed_password"),
            new DisplayName("Test User")
        );
        return userRepository.save(user);
    }
}
```

**学習ポイント**:
- ✅ **@DataJpaTest**: JPA関連のみをロード（軽量）
- ✅ **@Import**: 必要なBeanを追加
- ✅ **H2 Database**: テスト用インメモリDB（`application-test.yml`で設定）

---

### 3.3 REST APIのテスト

#### 3.3.1 MockMvcを使ったテスト

**ファイル**: `src/test/java/com/minislack/presentation/api/user/UserControllerTest.java`

```java
package com.minislack.presentation.api.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.minislack.application.user.RegisterUserCommand;
import com.minislack.application.user.UserQueryService;
import com.minislack.application.user.UserRegistrationService;
import com.minislack.domain.model.user.DisplayName;
import com.minislack.domain.model.user.Email;
import com.minislack.domain.model.user.Password;
import com.minislack.domain.model.user.User;
import com.minislack.domain.model.user.UserId;
import com.minislack.domain.model.user.Username;

/**
 * UserControllerのMockMvcテスト
 * コントローラー層のみをテスト
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRegistrationService registrationService;

    @MockBean
    private UserQueryService queryService;

    @Test
    void register_ValidRequest_Returns201Created() throws Exception {
        // Given
        UserId userId = UserId.newId();
        when(registrationService.registerUser(any(RegisterUserCommand.class)))
            .thenReturn(userId);

        String requestBody = """
            {
              "username": "testuser",
              "email": "test@example.com",
              "password": "password123",
              "displayName": "Test User"
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(userId.getValue()))
            .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

    @Test
    void getUserById_ExistingUser_Returns200Ok() throws Exception {
        // Given
        User user = createTestUser();
        when(queryService.findById(any(UserId.class)))
            .thenReturn(Optional.of(user));

        // When & Then
        mockMvc.perform(get("/api/v1/users/" + user.getUserId().getValue()))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(user.getUserId().getValue()))
            .andExpect(jsonPath("$.username").value("testuser"))
            .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    private User createTestUser() {
        return new User(
            UserId.newId(),
            new Username("testuser"),
            new Email("test@example.com"),
            Password.fromHashedValue("hashed_password"),
            new DisplayName("Test User")
        );
    }
}
```

**学習ポイント**:
- ✅ **@WebMvcTest**: コントローラーのみをテスト（軽量）
- ✅ **@MockBean**: サービスをモック化
- ✅ **MockMvc**: HTTPリクエストのシミュレーション
- ✅ **andDo(print())**: リクエスト/レスポンスの詳細を出力
- ✅ **jsonPath()**: JSONレスポンスの検証

---

## 4. E2Eテスト（End-to-End Test）

### 4.1 完全な統合テスト

**ファイル**: `src/test/java/com/minislack/e2e/UserRegistrationE2ETest.java`

```java
package com.minislack.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.minislack.presentation.api.user.UserRegistrationRequest;
import com.minislack.presentation.api.user.UserRegistrationResponse;

/**
 * ユーザー登録E2Eテスト
 * 実際のHTTPサーバーを起動してテスト
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserRegistrationE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void userRegistration_CompleteFlow_Success() {
        // Given
        UserRegistrationRequest request = new UserRegistrationRequest(
            "e2euser",
            "e2e@example.com",
            "password123",
            "E2E Test User"
        );

        // When: ユーザー登録
        ResponseEntity<UserRegistrationResponse> response = restTemplate.postForEntity(
            "/api/v1/users/register",
            request,
            UserRegistrationResponse.class
        );

        // Then: 登録成功
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getUserId());
        assertEquals("User registered successfully", response.getBody().getMessage());
    }
}
```

**学習ポイント**:
- ✅ **TestRestTemplate**: 実際のHTTPリクエスト
- ✅ **RANDOM_PORT**: ランダムポートでサーバー起動
- ✅ **完全な統合**: DB、Spring Security等すべて動作

---

## 5. テストカバレッジ

### 5.1 JaCoCo設定

**ファイル**: `build.gradle`に追加

```groovy
plugins {
    id 'jacoco'
}

jacoco {
    toolVersion = "0.8.11"
}

test {
    finalizedBy jacocoTestReport
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.80 // 80%以上
            }
        }
    }
}
```

### 5.2 カバレッジレポート生成

```bash
# テスト実行とカバレッジレポート生成
./gradlew test jacocoTestReport

# レポートを開く
open build/reports/jacoco/test/html/index.html
```

**目標カバレッジ**:
- ドメイン層: 95%以上
- アプリケーション層: 90%以上
- インフラ層: 80%以上
- プレゼンテーション層: 75%以上

---

## 6. テストデータビルダー

### 6.1 TestDataBuilder

**ファイル**: `src/test/java/com/minislack/testutil/TestDataBuilder.java`

```java
package com.minislack.testutil;

import com.minislack.domain.model.user.DisplayName;
import com.minislack.domain.model.user.Email;
import com.minislack.domain.model.user.IPasswordEncoder;
import com.minislack.domain.model.user.Password;
import com.minislack.domain.model.user.User;
import com.minislack.domain.model.user.UserId;
import com.minislack.domain.model.user.Username;

/**
 * テストデータビルダー
 * テストデータの作成を簡略化
 */
public class TestDataBuilder {

    public static class UserBuilder {
        private String username = "testuser";
        private String email = "test@example.com";
        private String displayName = "Test User";
        private String password = "hashed_password";

        public UserBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UserBuilder email(String email) {
            this.email = email;
            return this;
        }

        public UserBuilder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public User build() {
            return new User(
                UserId.newId(),
                new Username(username),
                new Email(email),
                Password.fromHashedValue(password),
                new DisplayName(displayName)
            );
        }
    }

    public static UserBuilder user() {
        return new UserBuilder();
    }
}
```

**使用例**:
```java
@Test
void test() {
    User user = TestDataBuilder.user()
        .username("customuser")
        .email("custom@example.com")
        .build();
    
    // テスト実行
}
```

**学習ポイント**:
- ✅ **Builderパターン**: 流れるようなAPI
- ✅ **デフォルト値**: 必要な値だけカスタマイズ

**発展：他のエンティティへの拡張**:

現在の実装は User 用ですが、他のエンティティにも同じパターンを適用できます：

```java
// Channel用のBuilder
public static class ChannelBuilder {
    private String channelName = "test-channel";
    private String description = "Test Description";
    private boolean isPublic = true;
    private String createdBy = "user-id";

    public ChannelBuilder channelName(String name) {
        this.channelName = name;
        return this;
    }

    public ChannelBuilder description(String desc) {
        this.description = desc;
        return this;
    }

    public ChannelBuilder isPublic(boolean pub) {
        this.isPublic = pub;
        return this;
    }

    public ChannelBuilder createdBy(String userId) {
        this.createdBy = userId;
        return this;
    }

    public Channel build() {
        return new Channel(
            ChannelId.newId(),
            new ChannelName(channelName),
            new Description(description),
            isPublic,
            UserId.of(createdBy)
        );
    }
}

public static ChannelBuilder channel() {
    return new ChannelBuilder();
}

// 使用例
Channel channel = TestDataBuilder.channel()
    .channelName("dev-channel")
    .isPublic(false)
    .createdBy(userId)
    .build();
```

このパターンを他のエンティティ（Message, ChannelMembership等）にも適用することで、テストデータの作成が大幅に簡略化されます。

---

## 7. テスト実行

### 7.1 全テスト実行

```bash
./gradlew test
```

### 7.2 特定のテストクラスのみ実行

```bash
./gradlew test --tests UserTest
./gradlew test --tests '*IntegrationTest'
```

### 7.3 継続的テスト実行

```bash
./gradlew test --continuous
```

ファイルが変更されると自動的にテスト実行されます。

---

## 8. テストのベストプラクティス

### 8.1 命名規則

**テストメソッド名**:
```java
void メソッド名_テスト条件_期待される結果() {
    // 例: registerUser_DuplicateEmail_ThrowsException
}
```

### 8.2 AAA パターン

```java
@Test
void testMethod() {
    // Arrange (Given): テストデータ準備
    User user = createTestUser();
    
    // Act (When): テスト対象メソッド実行
    user.changePassword(oldPassword, newPassword);
    
    // Assert (Then): 結果検証
    assertEquals(newPassword, user.getPassword());
}
```

### 8.3 1テスト1検証

```java
// ❌ 悪い例: 複数のことを検証
@Test
void testEverything() {
    user.changePassword(...);
    assertEquals(...);
    user.updateProfile(...);
    assertEquals(...);
}

// ✅ 良い例: 1つのことを検証
@Test
void changePassword_ValidInput_UpdatesPassword() {
    user.changePassword(...);
    assertEquals(...);
}

@Test
void updateProfile_ValidInput_UpdatesProfile() {
    user.updateProfile(...);
    assertEquals(...);
}
```

### 8.4 テストの独立性

```java
// ✅ 各テストは独立している
@Test
void test1() {
    User user = createTestUser(); // 毎回新規作成
    // ...
}

@Test
void test2() {
    User user = createTestUser(); // test1の影響を受けない
    // ...
}
```

---

## 9. まとめ

### 9.1 テスト戦略

1. **単体テスト**: ドメイン層、アプリケーション層のビジネスロジック
2. **統合テスト**: リポジトリ、コントローラー
3. **E2Eテスト**: 主要なユーザーシナリオ

### 9.2 テストツール

- ✅ **JUnit 5**: テストフレームワーク
- ✅ **Mockito**: モック化
- ✅ **MockMvc**: REST APIテスト
- ✅ **@SpringBootTest**: 統合テスト
- ✅ **@DataJpaTest**: JPA層テスト
- ✅ **JaCoCo**: カバレッジ計測

### 9.3 次のステップ

テスト実装が完了しました！最後にローカルデプロイメントガイドを作成します：

- [17-local-deployment.md](17-local-deployment.md) - ローカルデプロイメント

---

## 10. 参考資料

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito](https://site.mockito.org/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/reference/testing/index.html)
- [Test-Driven Development](https://martinfowler.com/bliki/TestDrivenDevelopment.html)

