# 環境構築ガイド

## 1. はじめに

このドキュメントでは、MiniSlackの開発環境をセットアップする手順を説明します。

### 1.1 必要なもの

- **Java 21**: アプリケーション実行環境（LTS版）
- **Docker & Docker Compose**: PostgreSQL、RabbitMQの実行
- **IDE**: IntelliJ IDEA（推奨）またはVS Code
- **Git**: ソースコード管理

---

## 2. Java 21のインストール

### 2.1 macOSの場合

**方法1: Homebrewを使用**

```bash
# Homebrewのインストール（未インストールの場合）
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# OpenJDK 21のインストール
brew install openjdk@21

# シンボリックリンクの作成
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-21.jdk

# 確認
java -version
# openjdk version "21.0.x" と表示されればOK
```

**方法2: SDKMANを使用（複数バージョン管理）**

```bash
# SDKMANのインストール
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Java 21のインストール
sdk install java 21-tem

# 確認
java -version
```

### 2.2 Windowsの場合

1. [Adoptium（Eclipse Temurin）](https://adoptium.net/)から`.msi`ファイルをダウンロード
2. インストーラーを実行
3. 環境変数`JAVA_HOME`を設定

```powershell
# PowerShellで確認
java -version
```

### 2.3 Linuxの場合

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-21-jdk

# CentOS/RHEL
sudo yum install java-21-openjdk-devel

# 確認
java -version
```

---

## 3. Dockerのインストール

### 3.1 macOSの場合

**Docker Desktopのインストール**

1. [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop/)をダウンロード
2. `.dmg`ファイルを実行してインストール
3. Dockerアプリを起動

```bash
# 確認
docker --version
docker-compose --version
```

### 3.2 Windowsの場合

1. [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/)をダウンロード
2. WSL2が有効になっていることを確認
3. インストーラーを実行

### 3.3 Linuxの場合

```bash
# Ubuntu
sudo apt update
sudo apt install docker.io docker-compose

# ユーザーをdockerグループに追加
sudo usermod -aG docker $USER

# 再ログインして確認
docker --version
```

---

## 4. IDEのセットアップ

### 4.1 IntelliJ IDEA（推奨）

**Community Edition（無料）のインストール**

1. [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download/)をダウンロード
2. インストーラーを実行

**初期設定**

1. 起動後、「New Project」を選択
2. Project SDK: Java 21を選択
3. Build System: Gradleを選択

**推奨プラグイン**:
- Lombok（ボイラープレートコード削減）
- SonarLint（コード品質チェック）
- Docker（Dockerファイルのサポート）

### 4.2 Visual Studio Code

**拡張機能のインストール**

1. [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)
2. [Spring Boot Extension Pack](https://marketplace.visualstudio.com/items?itemName=vmware.vscode-boot-dev-pack)
3. [Docker](https://marketplace.visualstudio.com/items?itemName=ms-azuretools.vscode-docker)

---

## 5. プロジェクト作成

### 5.1 Spring Initializrでプロジェクト生成

**方法1: Webブラウザから**

1. [Spring Initializr](https://start.spring.io/)にアクセス
2. 以下を設定：
   - **Project**: Gradle - Groovy
   - **Language**: Java
   - **Spring Boot**: 3.4.x（最新安定版）
   - **Project Metadata**:
     - Group: `com.minislack`
     - Artifact: `minislack`
     - Name: `minislack`
     - Package name: `com.minislack`
     - Packaging: Jar
     - Java: 21
   - **Dependencies**:
     - Spring Web
     - Spring Data JPA
     - PostgreSQL Driver
     - Spring for RabbitMQ
     - Lombok
     - Validation
     - Spring Boot DevTools

3. 「GENERATE」をクリックしてzipをダウンロード
4. 解凍してプロジェクトフォルダに配置

**方法2: コマンドラインから**

```bash
curl https://start.spring.io/starter.zip \
  -d type=gradle-project \
  -d language=java \
  -d bootVersion=3.4.11 \
  -d groupId=com.minislack \
  -d artifactId=minislack \
  -d name=minislack \
  -d packageName=com.minislack \
  -d javaVersion=21 \
  -d dependencies=web,data-jpa,postgresql,amqp,lombok,validation,devtools \
  -o minislack.zip

unzip minislack.zip
cd minislack
```

### 5.2 Gradleビルドファイルの確認

`build.gradle`の内容を確認します：

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.11'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.minislack'
version = '0.0.1-SNAPSHOT'
description = 'Demo project for Spring Boot'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-amqp'
    compileOnly 'org.projectlombok:lombok'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
    runtimeOnly 'org.postgresql:postgresql'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.amqp:spring-rabbit-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

**注意**: 
- Java 21を使用するため、`toolchain` APIを使用しています
- 初期段階では最小限の依存関係のみ含めています
- Security、JWT、MapStruct等は後の段階で追加予定です

### 5.3 プロジェクト構造の作成

```bash
cd minislack

# ドメイン層
mkdir -p src/main/java/com/minislack/domain/model/user
mkdir -p src/main/java/com/minislack/domain/model/channel
mkdir -p src/main/java/com/minislack/domain/model/message
mkdir -p src/main/java/com/minislack/domain/service
mkdir -p src/main/java/com/minislack/domain/event

# アプリケーション層
mkdir -p src/main/java/com/minislack/application/user
mkdir -p src/main/java/com/minislack/application/channel
mkdir -p src/main/java/com/minislack/application/message

# インフラ層
mkdir -p src/main/java/com/minislack/infrastructure/persistence/user
mkdir -p src/main/java/com/minislack/infrastructure/persistence/channel
mkdir -p src/main/java/com/minislack/infrastructure/persistence/message
mkdir -p src/main/java/com/minislack/infrastructure/messaging/rabbitmq
mkdir -p src/main/java/com/minislack/infrastructure/security

# プレゼンテーション層
mkdir -p src/main/java/com/minislack/presentation/api/user
mkdir -p src/main/java/com/minislack/presentation/api/channel
mkdir -p src/main/java/com/minislack/presentation/api/message
mkdir -p src/main/java/com/minislack/presentation/web

# テスト
mkdir -p src/test/java/com/minislack/domain/model/user
mkdir -p src/test/java/com/minislack/application/user
mkdir -p src/test/java/com/minislack/infrastructure/persistence/user
mkdir -p src/test/java/com/minislack/presentation/api/user

# リソース
mkdir -p src/main/resources/db/migration
mkdir -p src/main/resources/templates
mkdir -p src/main/resources/static
```

---

## 6. Docker Composeの設定

プロジェクトルートに`docker-compose.yml`を作成します：

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16
    container_name: minislack-postgres
    environment:
      POSTGRES_DB: minislack
      POSTGRES_USER: minislack
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - minislack-network

  rabbitmq:
    image: rabbitmq:3.12-management
    container_name: minislack-rabbitmq
    environment:
      RABBITMQ_DEFAULT_USER: minislack
      RABBITMQ_DEFAULT_PASS: password
    ports:
      - "5672:5672"   # AMQP port
      - "15672:15672" # Management UI
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq
    networks:
      - minislack-network

volumes:
  postgres-data:
  rabbitmq-data:

networks:
  minislack-network:
    driver: bridge
```

### 6.1 Dockerコンテナの起動

```bash
# コンテナの起動
docker-compose up -d

# 確認
docker-compose ps

# PostgreSQLに接続確認
docker exec -it minislack-postgres psql -U minislack -d minislack

# RabbitMQ管理画面にアクセス
# ブラウザで http://localhost:15672
# ユーザー名: minislack
# パスワード: password
```

### 6.2 コンテナの停止

```bash
# 停止
docker-compose down

# データも削除する場合
docker-compose down -v
```

---

## 7. Spring Boot設定ファイル

### 7.1 application.yml

`src/main/resources/application.yml`を作成：

```yaml
spring:
  application:
    name: minislack

  # データソース設定
  datasource:
    url: jdbc:postgresql://localhost:5432/minislack
    username: minislack
    password: password
    driver-class-name: org.postgresql.Driver

  # JPA設定
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect

  # RabbitMQ設定
  rabbitmq:
    host: localhost
    port: 5672
    username: minislack
    password: password

  # Actuator設定（注意: 現在Actuator依存関係は含まれていません）
  # Spring Boot Actuatorを使用する場合は、build.gradleに以下を追加:
  # implementation 'org.springframework.boot:spring-boot-starter-actuator'
  management:
    endpoints:
      web:
        exposure:
          include: health,metrics,info

# サーバー設定
server:
  port: 8080

# ロギング設定
logging:
  level:
    com.minislack: DEBUG
    org.springframework.web: INFO
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### 7.2 application-test.yml

テスト用の設定ファイル（`src/test/resources/application-test.yml`）：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password: 

  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
    database-platform: org.hibernate.dialect.H2Dialect

  rabbitmq:
    host: localhost
    port: 5672

logging:
  level:
    com.minislack: DEBUG
```

---

## 8. 動作確認

### 8.1 シンプルなヘルスチェックコントローラーの作成

まず、依存関係のない最小限のコントローラーを作成します。

`src/main/java/com/minislack/presentation/api/HealthController.java`を作成：

```java
package com.minislack.presentation.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("timestamp", LocalDateTime.now());
        response.put("message", "MiniSlack is running!");
        return response;
    }
}
```

このバージョンは外部依存がないため、すぐに起動できます。データベースやRabbitMQの接続確認は、セクション9と10で段階的に追加します。

### 8.2 アプリケーションの起動

**この時点では**: シンプルなHealthControllerは外部依存がないため、Docker Composeなしで起動できます。

**方法1: Gradleコマンド**

```bash
./gradlew bootRun
```

**方法2: IntelliJ IDEA**

1. `src/main/java/com/minislack/MinislackApplication.java`を開く
2. `main`メソッドの左側の▶︎アイコンをクリック
3. 「Run 'MinislackApplication'」を選択

**方法3: ビルドしてJARを実行**

```bash
./gradlew build
java -jar build/libs/minislack-0.0.1-SNAPSHOT.jar
```

### 8.3 動作確認

**ブラウザまたはcurlでアクセス**

```bash
curl http://localhost:8080/api/health
```

**期待される出力**:
```json
{
  "status": "OK",
  "timestamp": "2024-11-04T10:30:00.123456",
  "message": "MiniSlack is running!"
}
```

この時点では、Spring Bootアプリケーションが正常に起動していることのみを確認します。

**注意**: Actuatorエンドポイント（`/actuator/health`）は、現在の構成では利用できません。
Actuatorを有効にするには、`build.gradle`に以下を追加してください：
```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

---

## 9. データベース接続確認

**この時点から**: PostgreSQLが必要になります。Docker Composeを起動してください。

```bash
# PostgreSQLコンテナを起動
docker-compose up -d postgres

# 確認
docker-compose ps
# minislack-postgres が Up になっていることを確認
```

### 9.1 簡単なエンティティとリポジトリの作成

**エンティティ**（`src/main/java/com/minislack/infrastructure/persistence/TestEntity.java`）：

```java
package com.minislack.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "test_entities")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
}
```

**リポジトリ**（`src/main/java/com/minislack/infrastructure/persistence/TestRepository.java`）：

```java
package com.minislack.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TestRepository extends JpaRepository<TestEntity, Long> {
}
```

### 9.2 HealthControllerにDB接続確認を追加

既存の`HealthController.java`を更新して、データベース接続確認機能を追加します：

```java
package com.minislack.presentation.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minislack.infrastructure.persistence.TestEntity;
import com.minislack.infrastructure.persistence.TestRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final TestRepository testRepository;

    public HealthController(TestRepository testRepository) {
        this.testRepository = testRepository;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("timestamp", LocalDateTime.now());
        response.put("message", "MiniSlack is running!");
        response.put("dbRecordCount", testRepository.count());
        return response;
    }

    @PostMapping("/test")
    public TestEntity createTest(@RequestBody Map<String, String> body) {
        TestEntity entity = new TestEntity();
        entity.setName(body.get("name"));
        return testRepository.save(entity);
    }
}
```

**変更点**:
- `TestRepository`を依存性注入で追加
- `/api/health`に`dbRecordCount`フィールドを追加
- `/api/test`エンドポイントを追加（データ作成用）

**重要**: HealthControllerを更新したら、アプリケーションを再起動してください。

```bash
# 実行中のアプリケーションを停止（Ctrl+C）
# その後、再起動
./gradlew bootRun
```

### 9.3 動作確認

```bash
# 1. 初期状態のヘルスチェック（dbRecordCountは0）
curl http://localhost:8080/api/health
# 出力: {"status":"OK","timestamp":"...","message":"MiniSlack is running!","dbRecordCount":0}

# 2. テストデータ作成
curl -X POST http://localhost:8080/api/test \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Data"}'
# 出力: {"id":1,"name":"Test Data"}

# 3. 再度ヘルスチェック（dbRecordCountが1になる）
curl http://localhost:8080/api/health
# 出力: {"status":"OK","timestamp":"...","message":"MiniSlack is running!","dbRecordCount":1}
```

これにより、PostgreSQLへの接続とデータの永続化が正常に動作していることが確認できます。

---

## 10. RabbitMQ接続確認

**この時点から**: RabbitMQが必要になります。Docker Composeでコンテナを起動してください。

```bash
# RabbitMQコンテナを起動（既にPostgreSQLが起動している場合）
docker-compose up -d rabbitmq

# または、すべてのコンテナを起動
docker-compose up -d

# 確認
docker-compose ps
# minislack-rabbitmq が Up になっていることを確認
```

### 10.1 簡単なPublisher/Consumerの作成

**設定クラス**（`src/main/java/com/minislack/infrastructure/messaging/rabbitmq/RabbitMQConfig.java`）：

```java
package com.minislack.infrastructure.messaging.rabbitmq;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String TEST_QUEUE = "test-queue";

    @Bean
    public Queue testQueue() {
        return new Queue(TEST_QUEUE, true);
    }
}
```

**Publisher**（`src/main/java/com/minislack/infrastructure/messaging/rabbitmq/TestPublisher.java`）：

```java
package com.minislack.infrastructure.messaging.rabbitmq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TestPublisher {

    private final RabbitTemplate rabbitTemplate;

    public TestPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendMessage(String message) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.TEST_QUEUE, message);
    }
}
```

**Consumer**（`src/main/java/com/minislack/infrastructure/messaging/rabbitmq/TestConsumer.java`）：

```java
package com.minislack.infrastructure.messaging.rabbitmq;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TestConsumer {

    @RabbitListener(queues = RabbitMQConfig.TEST_QUEUE)
    public void receiveMessage(String message) {
        System.out.println("Received from RabbitMQ: " + message);
    }
}
```

### 10.2 HealthControllerにRabbitMQテストを追加

既存の`HealthController.java`を更新して、RabbitMQのテスト機能を追加します：

```java
package com.minislack.presentation.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minislack.infrastructure.messaging.rabbitmq.TestPublisher;
import com.minislack.infrastructure.persistence.TestEntity;
import com.minislack.infrastructure.persistence.TestRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final TestRepository testRepository;
    private final TestPublisher testPublisher;

    public HealthController(TestRepository testRepository, TestPublisher testPublisher) {
        this.testRepository = testRepository;
        this.testPublisher = testPublisher;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("timestamp", LocalDateTime.now());
        response.put("message", "MiniSlack is running!");
        response.put("dbRecordCount", testRepository.count());
        return response;
    }

    @PostMapping("/test")
    public TestEntity createTest(@RequestBody Map<String, String> body) {
        TestEntity entity = new TestEntity();
        entity.setName(body.get("name"));
        return testRepository.save(entity);
    }

    @PostMapping("/rabbitmq-test")
    public Map<String, String> testRabbitMQ(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        testPublisher.sendMessage(message);
        return Map.of("status", "Message sent to RabbitMQ");
    }
}
```

**変更点**:
- `TestPublisher`を依存性注入で追加
- `/api/rabbitmq-test`エンドポイントを追加

**重要**: HealthControllerを更新したら、アプリケーションを再起動してください。

```bash
# 実行中のアプリケーションを停止（Ctrl+C）
# その後、再起動
./gradlew bootRun
```

**注意**: この最終版のHealthControllerは動作確認用です。実際の本番コードでは：
- Presentation層がInfrastructure層に直接依存すべきではありません
- Application層を経由してビジネスロジックを実行すべきです
- 詳細はオニオンアーキテクチャのドキュメントを参照してください

### 10.3 動作確認

```bash
# RabbitMQにメッセージを送信
curl -X POST http://localhost:8080/api/rabbitmq-test \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello RabbitMQ!"}'

# 期待される出力:
# {"status":"Message sent to RabbitMQ"}

# Spring Bootのコンソールに以下が表示される:
# Received from RabbitMQ: Hello RabbitMQ!
```

**RabbitMQ管理画面での確認**:

1. ブラウザで `http://localhost:15672` にアクセス
2. ログイン（ユーザー名: `minislack`、パスワード: `password`）
3. 「Queues」タブで `test-queue` の状態を確認
4. メッセージが送受信されていることを確認

---

## 11. トラブルシューティング

### 11.1 Javaのバージョンエラー

```
Error: A JNI error has occurred, please check your installation and try again
```

**解決策**: Java 17がインストールされているか確認

```bash
java -version
echo $JAVA_HOME
```

### 11.2 Dockerコンテナが起動しない

```
ERROR: for postgres  Cannot start service postgres: Ports are not available
```

**解決策**: ポートが既に使用されている

```bash
# ポート使用状況の確認
lsof -i :5432
lsof -i :5672

# プロセスを停止するか、docker-compose.ymlでポートを変更
```

### 11.3 PostgreSQLに接続できない

```
org.postgresql.util.PSQLException: Connection refused
```

**解決策**:

```bash
# Dockerコンテナの状態確認
docker-compose ps

# ログ確認
docker-compose logs postgres

# コンテナ再起動
docker-compose restart postgres
```

### 11.4 Gradleビルドエラー

```
Could not resolve all dependencies
```

**解決策**:

```bash
# Gradleキャッシュをクリア
./gradlew clean build --refresh-dependencies
```

### 11.5 RabbitMQ関連のコンパイルエラー

```
エラー: パッケージorg.springframework.amqp.coreは存在しません
import org.springframework.amqp.core.Queue;
```

**原因**: RabbitMQの依存関係が`build.gradle`に含まれていない

**解決策**:

`build.gradle`に以下を追加：

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-amqp'
    testImplementation 'org.springframework.amqp:spring-rabbit-test'
}
```

### 11.6 import文の不足によるコンパイルエラー

```
エラー: シンボルを見つけられません
  シンボル:   クラス TestRepository
  場所: クラス HealthController
```

**原因**: 必要なクラスのimport文が不足している

**解決策**:

ファイルの先頭に必要なimport文を追加：

```java
import com.minislack.infrastructure.messaging.rabbitmq.TestPublisher;
import com.minislack.infrastructure.persistence.TestEntity;
import com.minislack.infrastructure.persistence.TestRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
```

**IDEの自動import機能を活用**:
- IntelliJ IDEA: `Alt + Enter` → `Import class`
- VS Code: `Ctrl + .` → `Import`

---

## 12. まとめ

環境構築が完了しました！

**段階別の確認項目**:

**セクション8完了時点**:
- ✅ Java 21がインストールされている
- ✅ Spring Bootアプリケーションが起動する（Docker不要）
- ✅ `/api/health`エンドポイントにアクセスできる

**セクション9完了時点**:
- ✅ Dockerが起動している
- ✅ PostgreSQLコンテナが稼働している
- ✅ データベースに接続できる（`dbRecordCount`が表示される）
- ✅ `/api/test`でデータ作成ができる

**セクション10完了時点（全機能）**:
- ✅ RabbitMQコンテナが稼働している
- ✅ RabbitMQでメッセージを送受信できる（`/api/rabbitmq-test`で確認）
- ✅ すべてのインフラが統合動作している

**次のステップ**:
- 実際にドメイン層の実装を始めます
- オニオンアーキテクチャに従った設計を学びます
- TDD（テスト駆動開発）で進めます

**技術スタックの整理**:
- **Java**: 21（LTS版）
- **Spring Boot**: 3.4.11
- **データベース**: PostgreSQL 16
- **メッセージキュー**: RabbitMQ 3.12
- **ビルドツール**: Gradle 8.14.3
- **アーキテクチャ**: オニオンアーキテクチャ（Clean Architecture）

---

## 13. 参考コマンド集

### 13.1 Docker関連

```bash
# コンテナ起動
docker-compose up -d

# コンテナ停止
docker-compose down

# コンテナ状態確認
docker-compose ps

# ログ確認
docker-compose logs -f postgres
docker-compose logs -f rabbitmq

# PostgreSQL接続
docker exec -it minislack-postgres psql -U minislack -d minislack

# RabbitMQ管理画面（ブラウザで開く）
open http://localhost:15672
# Windows: start http://localhost:15672
# Linux: xdg-open http://localhost:15672
```

### 13.2 Gradle関連

```bash
# Spring Boot起動
./gradlew bootRun

# ビルド
./gradlew build

# クリーンビルド
./gradlew clean build

# テスト実行
./gradlew test

# 依存関係の更新
./gradlew clean build --refresh-dependencies
```

### 13.3 動作確認用API

```bash
# ヘルスチェック
curl http://localhost:8080/api/health

# テストデータ作成（PostgreSQL）
curl -X POST http://localhost:8080/api/test \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Data"}'

# RabbitMQテスト
curl -X POST http://localhost:8080/api/rabbitmq-test \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello RabbitMQ!"}'
```

### 13.4 トラブルシューティング

```bash
# Javaバージョン確認
java -version

# Gradleバージョン確認
./gradlew --version

# Dockerバージョン確認
docker --version
docker-compose --version

# ポート使用状況確認（macOS/Linux）
lsof -i :8080  # Spring Boot
lsof -i :5432  # PostgreSQL
lsof -i :5672  # RabbitMQ
lsof -i :15672 # RabbitMQ管理画面

# ポート使用状況確認（Windows PowerShell）
netstat -ano | findstr :8080
netstat -ano | findstr :5432
netstat -ano | findstr :5672
```

