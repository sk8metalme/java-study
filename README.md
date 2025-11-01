# MiniSlack - Java + Spring Boot ハンズオン学習プロジェクト

Java、Spring Boot、オニオンアーキテクチャを学ぶための実践的なチャットアプリケーション開発プロジェクトです。

## プロジェクト概要

Slack風のチャットアプリケーション「MiniSlack」を通じて、以下を段階的に学習します：

- **Java 17**: モダンJavaの機能
- **Spring Boot 3.4**: 最新のSpring Boot
- **オニオンアーキテクチャ**: ドメイン駆動設計（DDD）
- **REST API設計**: RESTful原則
- **RabbitMQ**: 非同期メッセージング
- **PostgreSQL**: リレーショナルデータベース
- **テスト駆動開発**: JUnit 5 + Mockito

## 主な機能

- ユーザー登録・認証（JWT）
- チャンネル作成・参加
- メッセージ送受信（REST API）
- リアルタイム通知（RabbitMQ）
- メッセージアーカイブバッチ処理
- Web UI（Thymeleaf）

## 技術スタック

- **言語**: Java 17
- **フレームワーク**: Spring Boot 3.4
- **アーキテクチャ**: Onion Architecture
- **データベース**: PostgreSQL 16
- **メッセージキュー**: RabbitMQ 3.12
- **ビルドツール**: Gradle 8.x
- **テスト**: JUnit 5, Mockito, H2 Database

## 必要な環境

- Java 17以上
- Docker & Docker Compose
- IDE（IntelliJ IDEA推奨）またはVS Code

## セットアップ手順

### 1. リポジトリのクローン

```bash
git clone <repository-url>
cd java-study
```

### 2. Dockerコンテナの起動

```bash
# PostgreSQLとRabbitMQを起動
docker-compose up -d

# 確認
docker-compose ps
```

### 3. アプリケーションの起動

```bash
# Gradle経由で起動
./gradlew bootRun

# または、IDEから MinislackApplication.java を実行
```

### 4. 動作確認

```bash
# ヘルスチェック
curl http://localhost:8080/actuator/health

# RabbitMQ管理画面
open http://localhost:15672
# ユーザー名: minislack
# パスワード: password
```

## ドキュメント

ハンズオン形式の学習ドキュメントは `docs/` ディレクトリにあります：

### Phase 1: 要件定義・設計
- [01-requirements.md](docs/01-requirements.md) - 要件定義書
- [02-architecture-overview.md](docs/02-architecture-overview.md) - オニオンアーキテクチャ概要
- [03-domain-model.md](docs/03-domain-model.md) - ドメインモデル設計

### Phase 2: 環境構築
- [04-environment-setup.md](docs/04-environment-setup.md) - 環境セットアップ

### Phase 3: レイヤー別実装
- [05-domain-layer.md](docs/05-domain-layer.md) - ドメイン層実装
- [06-application-layer.md](docs/06-application-layer.md) - アプリケーション層実装
- [07-infrastructure-layer.md](docs/07-infrastructure-layer.md) - インフラ層実装
- [08-presentation-layer.md](docs/08-presentation-layer.md) - プレゼンテーション層実装

### Phase 4: 機能実装
- [09-user-management.md](docs/09-user-management.md) - ユーザー管理機能
- [10-channel-management.md](docs/10-channel-management.md) - チャンネル管理機能
- [11-message-api.md](docs/11-message-api.md) - メッセージAPI
- [12-realtime-notification.md](docs/12-realtime-notification.md) - リアルタイム通知
- [13-batch-processing.md](docs/13-batch-processing.md) - バッチ処理
- [14-web-ui.md](docs/14-web-ui.md) - Web UI

### Phase 5: テスト・デプロイ
- [15-testing.md](docs/15-testing.md) - テスト実装
- [16-local-deployment.md](docs/16-local-deployment.md) - ローカルデプロイ

## プロジェクト構造

```
java-study/
├── docs/                          # ハンズオンドキュメント
├── src/
│   ├── main/
│   │   ├── java/com/minislack/
│   │   │   ├── domain/            # ドメイン層
│   │   │   │   ├── model/         # エンティティ・値オブジェクト
│   │   │   │   └── service/       # ドメインサービス
│   │   │   ├── application/       # アプリケーション層
│   │   │   │   ├── user/          # ユーザーユースケース
│   │   │   │   ├── channel/       # チャンネルユースケース
│   │   │   │   └── message/       # メッセージユースケース
│   │   │   ├── infrastructure/    # インフラ層
│   │   │   │   ├── persistence/   # リポジトリ実装
│   │   │   │   ├── messaging/     # RabbitMQ
│   │   │   │   └── security/      # セキュリティ実装
│   │   │   └── presentation/      # プレゼンテーション層
│   │   │       ├── api/            # REST API
│   │   │       └── web/            # Webコントローラー
│   │   └── resources/
│   │       ├── application.yml     # アプリケーション設定
│   │       └── templates/          # Thymeleafテンプレート
│   └── test/                       # テストコード
├── docker-compose.yml              # Docker設定
├── build.gradle                    # Gradleビルド設定
└── README.md                       # このファイル
```

## よく使うコマンド

```bash
# アプリケーション起動
./gradlew bootRun

# テスト実行
./gradlew test

# ビルド
./gradlew build

# コード整形
./gradlew spotlessApply

# Dockerコンテナ起動
docker-compose up -d

# Dockerコンテナ停止
docker-compose down

# PostgreSQL接続
docker exec -it minislack-postgres psql -U minislack -d minislack
```

## 学習の進め方

1. **要件定義を読む** (`docs/01-requirements.md`)
2. **アーキテクチャを理解する** (`docs/02-architecture-overview.md`)
3. **ドメインモデルを理解する** (`docs/03-domain-model.md`)
4. **環境構築** (`docs/04-environment-setup.md`)
5. **各レイヤーを順番に実装** (ドメイン → アプリケーション → インフラ → プレゼンテーション)
6. **機能を段階的に実装** (ユーザー管理 → チャンネル管理 → メッセージ → ...)
7. **テストを書く** (`docs/15-testing.md`)

## トラブルシューティング

### Dockerコンテナが起動しない

```bash
# ポート使用状況確認
lsof -i :5432
lsof -i :5672

# ログ確認
docker-compose logs postgres
docker-compose logs rabbitmq
```

### Gradleビルドエラー

```bash
# キャッシュクリア
./gradlew clean build --refresh-dependencies
```

## ライセンス

このプロジェクトは学習目的で作成されています。

## 参考資料

- [Spring Boot公式ドキュメント](https://spring.io/projects/spring-boot)
- [オニオンアーキテクチャ](https://jeffreypalermo.com/2008/07/the-onion-architecture-part-1/)
- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [ドメイン駆動設計](https://www.domainlanguage.com/ddd/)