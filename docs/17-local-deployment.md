# ローカルデプロイメントガイド

## 1. はじめに

このドキュメントでは、MiniSlackを本番に近い環境でローカルデプロイする方法を説明します。

### 1.1 デプロイメント方法

- **開発モード**: `./gradlew bootRun`（ホットリロード有効）
- **本番モード**: JARファイルを作成して実行
- **Docker**: Dockerイメージを作成して実行

---

## 2. 開発モードでの起動

### 2.1 前提条件確認

```bash
# Java 17確認
java -version

# Docker起動確認
docker-compose ps

# PostgreSQL, RabbitMQ起動
docker-compose up -d
```

### 2.2 アプリケーション起動

```bash
./gradlew bootRun
```

**特徴**:
- DevToolsによるホットリロード
- デバッグモード有効
- SQL ログ出力

**確認**:
```bash
curl http://localhost:8080/actuator/health
```

---

## 3. 本番モードでのデプロイ

### 3.1 JARファイルのビルド

```bash
# ビルド
./gradlew clean build

# 成果物の確認
ls -lh build/libs/
# minislack-0.0.1-SNAPSHOT.jar が生成される
```

### 3.2 本番用設定ファイル

**ファイル**: `src/main/resources/application-prod.yml`

```yaml
spring:
  application:
    name: minislack

  datasource:
    url: jdbc:postgresql://localhost:5432/minislack
    username: minislack
    password: ${DB_PASSWORD:password}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10

  jpa:
    hibernate:
      ddl-auto: validate  # 本番ではvalidateまたはnone
    show-sql: false       # SQLログは無効化
    properties:
      hibernate:
        format_sql: false

  rabbitmq:
    host: localhost
    port: 5672
    username: minislack
    password: ${RABBITMQ_PASSWORD:password}

server:
  port: 8080
  compression:
    enabled: true
    min-response-size: 1024

jwt:
  secret: ${JWT_SECRET}  # 環境変数から取得（必須）
  expiration: 86400000

logging:
  level:
    root: INFO
    com.minislack: INFO
  file:
    name: logs/minislack.log
  pattern:
    file: '%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n'
```

### 3.3 環境変数の設定

```bash
# .envファイルを作成
cat > .env << 'EOF'
DB_PASSWORD=your_secure_password
RABBITMQ_PASSWORD=your_rabbitmq_password
JWT_SECRET=your_jwt_secret_key_at_least_256_bits
EOF

# 環境変数を読み込んで実行
export $(cat .env | xargs)
java -jar build/libs/minislack-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

---

## 4. Dockerでのデプロイ

### 4.1 Dockerfile

**ファイル**: `Dockerfile`

```dockerfile
# マルチステージビルド
FROM gradle:8-jdk17 AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN gradle clean bootJar --no-daemon

# 実行環境
FROM eclipse-temurin:17-jre
WORKDIR /app

# 非rootユーザーで実行
RUN groupadd -r spring && useradd -r -g spring spring

# JARファイルをコピー
COPY --from=builder /app/build/libs/*.jar app.jar

# 所有権変更
RUN chown spring:spring app.jar

USER spring:spring

EXPOSE 8080

# ヘルスチェック
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**学習ポイント**:
- ✅ **マルチステージビルド**: イメージサイズ削減
- ✅ **非rootユーザー**: セキュリティ向上
- ✅ **ヘルスチェック**: コンテナの健全性確認

**⚠️ ビルドキャッシュ最適化（推奨）**:

本番環境での高速ビルドのため、依存関係の解決をキャッシュレイヤーとして分離することを推奨します：

```dockerfile
# 最適化版Dockerfile
FROM gradle:8-jdk17 AS builder
WORKDIR /app

# 依存関係の解決をキャッシュ
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle --no-daemon dependencies

# ソースコードをコピー（上記のキャッシュを活用）
COPY src ./src
RUN gradle clean bootJar --no-daemon

# 実行環境（変更なし）
FROM eclipse-temurin:17-jre
# ...
```

このように分離することで、ソースコード変更時も依存解決のキャッシュが再利用され、ビルド時間が短縮されます。

### 4.2 docker-compose.ymlの拡張

**ファイル**: `docker-compose.prod.yml`

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16
    container_name: minislack-postgres
    environment:
      POSTGRES_DB: minislack
      POSTGRES_USER: minislack
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - minislack-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U minislack"]
      interval: 10s
      timeout: 5s
      retries: 5

  rabbitmq:
    image: rabbitmq:3.12-management
    container_name: minislack-rabbitmq
    environment:
      RABBITMQ_DEFAULT_USER: minislack
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD}
    ports:
      - "5672:5672"
      - "15672:15672"
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq
    networks:
      - minislack-network
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    build: .
    container_name: minislack-app
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/minislack
      SPRING_DATASOURCE_USERNAME: minislack
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_PORT: 5672
      SPRING_RABBITMQ_USERNAME: minislack
      SPRING_RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
    ports:
      - "8080:8080"
    networks:
      - minislack-network
    depends_on:
      postgres:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy

volumes:
  postgres-data:
  rabbitmq-data:

networks:
  minislack-network:
    driver: bridge
```

### 4.3 Dockerでの起動

```bash
# イメージビルドと起動
docker-compose -f docker-compose.prod.yml up -d --build

# ログ確認
docker-compose -f docker-compose.prod.yml logs -f app

# 停止
docker-compose -f docker-compose.prod.yml down
```

**⚠️ セキュリティに関する注意**:

上記の`docker-compose.prod.yml`では、PostgreSQL（5432）とRabbitMQ（5672, 15672）のポートが外部に公開されています。

**本番環境での推奨事項**:

PostgreSQLとRabbitMQは外部からのアクセスが不要な場合、ポート公開を削除してください：

```yaml
# セキュアな設定例
postgres:
  # ports セクションを削除またはコメント化
  # - "5432:5432"  # 削除
  # appサービスからは postgres:5432 でネットワーク経由アクセス可能

rabbitmq:
  ports:
    # - "5672:5672"   # 削除（内部通信のみ）
    - "15672:15672"  # 管理画面は必要に応じて保持（VPN経由アクセス等）
```

**理由**:
- appサービスからは`postgres:5432`（コンテナ名:ポート）で接続可能
- ホストマシンの5432ポートを開放する必要なし
- セキュリティリスクを低減

**ローカル開発時**:
- データベースに直接接続したい場合は、ポート公開を保持
- または`docker exec`コマンドで接続

```bash
# ポート公開なしでもPostgreSQLに接続可能
docker exec -it minislack-postgres psql -U minislack -d minislack
```

---

## 5. 監視とヘルスチェック

### 5.1 Actuatorエンドポイント

```bash
# ヘルスチェック
curl http://localhost:8080/actuator/health

# メトリクス
curl http://localhost:8080/actuator/metrics

# JVMメモリ
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# HTTPリクエスト数
curl http://localhost:8080/actuator/metrics/http.server.requests
```

### 5.2 ログ確認

**アプリケーションログ**:
```bash
# Dockerの場合
docker-compose logs -f app

# JARで起動の場合
tail -f logs/minislack.log
```

**PostgreSQLログ**:
```bash
docker-compose logs -f postgres
```

**RabbitMQログ**:
```bash
docker-compose logs -f rabbitmq
```

---

## 6. パフォーマンスチューニング

### 6.1 JVM設定

```bash
java -Xms512m -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar build/libs/minislack-0.0.1-SNAPSHOT.jar
```

**パラメータ説明**:
- `-Xms512m`: 初期ヒープサイズ
- `-Xmx2g`: 最大ヒープサイズ
- `-XX:+UseG1GC`: G1ガベージコレクタ使用
- `-XX:MaxGCPauseMillis=200`: GC一時停止時間の目標

### 6.2 データベース接続プール

`application-prod.yml`で設定：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### 6.3 RabbitMQ設定

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        concurrency: 5    # 同時実行数
        max-concurrency: 10
        prefetch: 20      # プリフェッチ数
```

---

## 7. バックアップとリストア

### 7.1 PostgreSQLバックアップ

```bash
# データベース全体のバックアップ
docker exec minislack-postgres pg_dump -U minislack minislack > backup_$(date +%Y%m%d).sql

# 圧縮
gzip backup_$(date +%Y%m%d).sql
```

### 7.2 リストア

```bash
# バックアップからリストア
gunzip backup_20250101.sql.gz
docker exec -i minislack-postgres psql -U minislack minislack < backup_20250101.sql
```

### 7.3 自動バックアップスクリプト

**ファイル**: `scripts/backup.sh`

```bash
#!/bin/bash

BACKUP_DIR="./backups"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/minislack_$DATE.sql"

mkdir -p $BACKUP_DIR

echo "Starting backup..."
docker exec minislack-postgres pg_dump -U minislack minislack > $BACKUP_FILE

if [ $? -eq 0 ]; then
    gzip $BACKUP_FILE
    echo "Backup completed: $BACKUP_FILE.gz"
    
    # 7日以上古いバックアップを削除
    find $BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete
else
    echo "Backup failed"
    exit 1
fi
```

```bash
# 実行権限付与
chmod +x scripts/backup.sh

# crontabに登録（毎日深夜3時）
0 3 * * * /path/to/scripts/backup.sh
```

---

## 8. トラブルシューティング

### 8.1 メモリ不足

**症状**: `java.lang.OutOfMemoryError`

**解決**:
```bash
# ヒープサイズを増やす
java -Xmx4g -jar build/libs/minislack-0.0.1-SNAPSHOT.jar
```

### 8.2 データベース接続プールの枯渇

**症状**: `Connection is not available`

**解決**:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30  # 増やす
```

### 8.3 RabbitMQメモリ不足

**症状**: RabbitMQがメッセージを拒否

**解決**:
```bash
# docker-compose.ymlに追加
rabbitmq:
  environment:
    RABBITMQ_VM_MEMORY_HIGH_WATERMARK: 1GB
```

---

## 9. セキュリティチェックリスト

### 9.1 本番環境での必須設定

- [ ] JWT_SECRETを環境変数から取得
- [ ] データベースパスワードを強力なものに変更
- [ ] CSRF保護を有効化（Web UIがある場合）
- [ ] HTTPS/TLS設定
- [ ] CORS設定を制限（特定のオリジンのみ許可）
- [ ] Spring Security設定の見直し
- [ ] Actuatorエンドポイントの保護

### 9.2 セキュリティ強化

**application-prod.yml**に追加：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized

spring:
  security:
    user:
      name: admin
      password: ${ADMIN_PASSWORD}
```

---

## 10. まとめ

### 10.1 デプロイ方法の比較

| 方法 | 用途 | メリット | デメリット |
|-----|------|---------|----------|
| **bootRun** | 開発 | ホットリロード、デバッグ容易 | 遅い |
| **JAR実行** | 本番（単一サーバー） | シンプル、ポータブル | スケール困難 |
| **Docker** | 本番（コンテナ） | 環境統一、スケーラブル | 学習コスト |

### 10.2 チェックリスト

**デプロイ前**:
- [ ] 全テストが成功している（`./gradlew test`）
- [ ] ビルドが成功している（`./gradlew build`）
- [ ] 環境変数が設定されている
- [ ] データベースマイグレーションが完了している
- [ ] 依存サービス（PostgreSQL, RabbitMQ）が起動している

**デプロイ後**:
- [ ] ヘルスチェックが成功している
- [ ] ログにエラーがない
- [ ] REST APIが応答する
- [ ] Web UIにアクセスできる
- [ ] RabbitMQが動作している

### 10.3 次のステップ

ローカルデプロイメントガイドが完了しました！

**学習の総まとめ**:
- ✅ オニオンアーキテクチャの理解
- ✅ ドメイン駆動設計の実践
- ✅ Spring Bootの習得
- ✅ REST API設計
- ✅ RabbitMQによる非同期処理
- ✅ Spring Batchによるバッチ処理
- ✅ Thymeleafによる Web UI
- ✅ 包括的なテスト戦略
- ✅ Dockerによるデプロイ

**発展的な学習**:
1. JWT認証の完全実装
2. WebSocketによるリアルタイム通信
3. Kubernetes へのデプロイ
4. CI/CDパイプライン構築
5. Elasticsearchによる全文検索
6. Redis によるキャッシング
7. マイクロサービス化

---

## 11. よくあるデプロイエラー

### 11.1 ポート競合

**エラー**: `Port 8080 was already in use`

**解決**:
```bash
# ポート使用状況確認
lsof -i :8080

# プロセスを停止
kill -9 <PID>

# または別のポートを使用
java -jar build/libs/minislack-0.0.1-SNAPSHOT.jar --server.port=8081
```

### 11.2 データベース接続失敗

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

### 11.3 JARファイルが大きすぎる

**問題**: JARファイルが100MB以上

**解決**: 不要な依存関係を除外
```groovy
dependencies {
    implementation('org.springframework.boot:spring-boot-starter-web') {
        exclude group: 'org.springframework.boot', module: 'spring-boot-starter-tomcat'
    }
    implementation 'org.springframework.boot:spring-boot-starter-jetty'
}
```

---

## 12. パフォーマンスモニタリング

### 12.1 Actuatorメトリクス

```bash
# JVMメモリ使用量
curl http://localhost:8080/actuator/metrics/jvm.memory.used | jq

# HTTPリクエスト数
curl http://localhost:8080/actuator/metrics/http.server.requests | jq

# データベース接続プール
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active | jq
```

### 12.2 ログ分析

```bash
# エラーログの抽出
grep "ERROR" logs/minislack.log

# 警告ログの抽出
grep "WARN" logs/minislack.log

# 特定のログの検索
grep "DuplicateResourceException" logs/minislack.log
```

---

## 13. 本番環境への展開（参考）

### 13.1 クラウドプラットフォーム

**AWS**:
- EC2: 仮想サーバー
- RDS: PostgreSQL
- Amazon MQ: RabbitMQ互換
- Elastic Beanstalk: アプリケーション管理

**GCP**:
- Cloud Run: コンテナ実行
- Cloud SQL: PostgreSQL
- Cloud Pub/Sub: メッセージング

**Azure**:
- App Service: Webアプリ
- Azure Database for PostgreSQL
- Azure Service Bus

### 13.2 CI/CD（参考）

**GitHub Actions例**:

```yaml
name: CI/CD

on:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - run: ./gradlew test
      - run: ./gradlew build
      - uses: docker/build-push-action@v4
        with:
          push: true
          tags: myregistry/minislack:latest
```

---

## 14. まとめ

おめでとうございます！MiniSlackプロジェクトの全ハンズオンが完了しました🎉

### 14.1 習得した技術

**アーキテクチャ**:
- オニオンアーキテクチャ
- ドメイン駆動設計（DDD）
- CQRS
- イベント駆動アーキテクチャ

**Spring Boot**:
- Spring Web（REST API）
- Spring Data JPA
- Spring Security
- Spring AMQP（RabbitMQ）
- Spring Batch

**データベース**:
- PostgreSQL
- JPA/Hibernate
- トランザクション管理

**非同期処理**:
- RabbitMQ
- Publisher/Subscriber

**テスト**:
- JUnit 5
- Mockito
- MockMvc
- 統合テスト

**デプロイ**:
- Docker
- Docker Compose
- 本番環境設定

### 14.2 次に学ぶべきこと

1. **JWT認証の完全実装**
2. **WebSocketでリアルタイムチャット**
3. **Kubernetes でのデプロイ**
4. **マイクロサービスアーキテクチャ**
5. **Elasticsearchで全文検索**
6. **Redisでキャッシング**
7. **Observability**（Prometheus, Grafana）

---

## 15. 参考資料

- [Spring Boot Production Ready](https://docs.spring.io/spring-boot/reference/actuator/index.html)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [12 Factor App](https://12factor.net/)
- [Clean Architecture in Practice](https://www.amazon.com/dp/0134494164)

