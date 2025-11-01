# バッチ処理実装ハンズオン

## 1. はじめに

このドキュメントでは、Spring Batchを使ったバッチ処理を実装します。

### 1.1 バッチ処理とは？

**バッチ処理**は、大量のデータを**一括で**処理する仕組みです。

**ユースケース**:
- 古いメッセージのアーカイブ（90日以上前）
- 統計情報の集計（日次）
- データクリーンアップ
- レポート生成

### 1.2 実装するバッチジョブ

1. **メッセージアーカイブジョブ**: 90日以上前のメッセージをアーカイブテーブルに移動
2. **統計集計ジョブ**: チャンネル別・ユーザー別のメッセージ数を集計

---

## 2. Spring Batchの基礎知識

### 2.1 主要概念

| 用語 | 説明 |
|-----|------|
| **Job** | バッチ処理全体 |
| **Step** | Jobを構成する処理単位 |
| **ItemReader** | データ読み込み |
| **ItemProcessor** | データ加工 |
| **ItemWriter** | データ書き込み |
| **Chunk** | 一度に処理する件数 |

### 2.2 処理フロー

```
Job
 └── Step
      ├── ItemReader: データベースから読み込み
      ├── ItemProcessor: 変換・加工
      └── ItemWriter: データベースに書き込み
      
繰り返し（Chunkサイズごと）
```

---

## 3. Step 1: アーカイブテーブルの作成

### 3.1 MessageArchiveJpaEntity

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/message/MessageArchiveJpaEntity.java`

```java
package com.minislack.infrastructure.persistence.message;

import java.time.LocalDateTime;

import org.springframework.lang.NonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * メッセージアーカイブJPAエンティティ
 */
@Entity
@Table(name = "message_archives")
public class MessageArchiveJpaEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long archiveId;

    @Column(name = "message_id", nullable = false, length = 36)
    private String messageId;

    @Column(name = "channel_id", nullable = false, length = 36)
    private String channelId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "archived_at", nullable = false)
    private LocalDateTime archivedAt;

    protected MessageArchiveJpaEntity() {
    }

    // Getter / Setter
    public Long getArchiveId() { return archiveId; }
    public void setArchiveId(Long archiveId) { this.archiveId = archiveId; }

    @NonNull
    public String getMessageId() { return messageId; }
    public void setMessageId(@NonNull String messageId) { this.messageId = messageId; }

    @NonNull
    public String getChannelId() { return channelId; }
    public void setChannelId(@NonNull String channelId) { this.channelId = channelId; }

    @NonNull
    public String getUserId() { return userId; }
    public void setUserId(@NonNull String userId) { this.userId = userId; }

    @NonNull
    public String getContent() { return content; }
    public void setContent(@NonNull String content) { this.content = content; }

    @NonNull
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(@NonNull LocalDateTime createdAt) { this.createdAt = createdAt; }

    @NonNull
    public LocalDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(@NonNull LocalDateTime archivedAt) { this.archivedAt = archivedAt; }
}
```

---

## 4. Step 2: バッチ設定

### 4.1 BatchConfiguration

**ファイル**: `src/main/java/com/minislack/infrastructure/batch/BatchConfiguration.java`

```java
package com.minislack.infrastructure.batch;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Batch設定
 */
@Configuration
@EnableBatchProcessing
public class BatchConfiguration {
}
```

**学習ポイント**:
- ✅ **@EnableBatchProcessing**: Spring Batchの自動設定を有効化
- ✅ **最小限の設定**: これだけでSpring Batchが動作

**重要な補足**:

Spring Batchは起動時に自動的に以下のメタデータテーブルを作成します：

- `batch_job_instance`: ジョブインスタンス
- `batch_job_execution`: ジョブ実行履歴
- `batch_job_execution_params`: ジョブパラメータ
- `batch_step_execution`: ステップ実行履歴
- `batch_step_execution_context`: ステップコンテキスト
- `batch_job_execution_context`: ジョブコンテキスト

これらのテーブルは、Hibernate `ddl-auto: update`または`create`設定の場合に自動作成されます。

**確認方法**:
```sql
-- PostgreSQLで確認
\dt batch*

-- テーブル一覧を表示
SELECT table_name FROM information_schema.tables 
WHERE table_name LIKE 'batch%';
```

---

## 5. Step 3: メッセージアーカイブジョブ

### 5.1 MessageArchiveJobConfig

**ファイル**: `src/main/java/com/minislack/infrastructure/batch/MessageArchiveJobConfig.java`

```java
package com.minislack.infrastructure.batch;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.transaction.PlatformTransactionManager;

import com.minislack.infrastructure.persistence.message.MessageArchiveJpaEntity;
import com.minislack.infrastructure.persistence.message.MessageJpaEntity;
import com.minislack.infrastructure.persistence.message.SpringDataMessageArchiveRepository;
import com.minislack.infrastructure.persistence.message.SpringDataMessageRepository;

/**
 * メッセージアーカイブバッチジョブ設定
 */
@Configuration
public class MessageArchiveJobConfig {
    
    private final SpringDataMessageRepository messageRepository;
    private final SpringDataMessageArchiveRepository archiveRepository;

    public MessageArchiveJobConfig(@NonNull SpringDataMessageRepository messageRepository,
                                  @NonNull SpringDataMessageArchiveRepository archiveRepository) {
        this.messageRepository = Objects.requireNonNull(messageRepository);
        this.archiveRepository = Objects.requireNonNull(archiveRepository);
    }

    /**
     * ItemReader: 90日以上前のメッセージを読み込み
     */
    @Bean
    @NonNull
    public RepositoryItemReader<MessageJpaEntity> messageArchiveReader() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(90);
        
        return new RepositoryItemReaderBuilder<MessageJpaEntity>()
            .name("messageArchiveReader")
            .repository(messageRepository)
            .methodName("findByCreatedAtBefore")
            .arguments(threshold)
            .sorts(Sort.by("createdAt").ascending())
            .pageSize(100)
            .build();
    }

    /**
     * ItemProcessor: メッセージをアーカイブエンティティに変換
     */
    @Bean
    @NonNull
    public ItemProcessor<MessageJpaEntity, MessageArchiveJpaEntity> messageArchiveProcessor() {
        return message -> {
            MessageArchiveJpaEntity archive = new MessageArchiveJpaEntity();
            archive.setMessageId(message.getMessageId());
            archive.setChannelId(message.getChannelId());
            archive.setUserId(message.getUserId());
            archive.setContent(message.getContent());
            archive.setCreatedAt(message.getCreatedAt());
            archive.setArchivedAt(LocalDateTime.now());
            return archive;
        };
    }

    /**
     * ItemWriter: アーカイブテーブルに書き込み
     */
    @Bean
    @NonNull
    public RepositoryItemWriter<MessageArchiveJpaEntity> messageArchiveWriter() {
        return new RepositoryItemWriterBuilder<MessageArchiveJpaEntity>()
            .repository(archiveRepository)
            .build();
    }

    /**
     * Step: アーカイブステップ
     */
    @Bean
    @NonNull
    public Step archiveStep(@NonNull JobRepository jobRepository,
                           @NonNull PlatformTransactionManager transactionManager) {
        return new StepBuilder("archiveStep", jobRepository)
            .<MessageJpaEntity, MessageArchiveJpaEntity>chunk(100, transactionManager)
            .reader(messageArchiveReader())
            .processor(messageArchiveProcessor())
            .writer(messageArchiveWriter())
            .build();
    }

    /**
     * Job: メッセージアーカイブジョブ
     */
    @Bean
    @NonNull
    public Job messageArchiveJob(@NonNull JobRepository jobRepository,
                                @NonNull Step archiveStep) {
        return new JobBuilder("messageArchiveJob", jobRepository)
            .start(archiveStep)
            .build();
    }
}
```

**学習ポイント**:
- ✅ **Chunk指向処理**: 100件ずつ処理
- ✅ **RepositoryItemReader**: Spring Data JPAから読み込み
- ✅ **ItemProcessor**: 変換処理
- ✅ **RepositoryItemWriter**: Spring Data JPAに書き込み

### 5.2 Spring Data JPA Repository（アーカイブ用）

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/message/SpringDataMessageArchiveRepository.java`

```java
package com.minislack.infrastructure.persistence.message;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMessageArchiveRepository extends JpaRepository<MessageArchiveJpaEntity, Long> {
}
```

---

## 6. Step 4: スケジューラー設定

### 6.1 スケジューラー有効化

**ファイル**: `src/main/java/com/minislack/MinislackApplication.java`を更新

```java
package com.minislack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 追加
public class MinislackApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinislackApplication.class, args);
    }
}
```

### 6.2 スケジュールジョブランナー

**ファイル**: `src/main/java/com/minislack/infrastructure/batch/MessageArchiveJobScheduler.java`

```java
package com.minislack.infrastructure.batch;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * メッセージアーカイブジョブスケジューラー
 */
@Component
public class MessageArchiveJobScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageArchiveJobScheduler.class);
    
    private final JobLauncher jobLauncher;
    private final Job messageArchiveJob;

    public MessageArchiveJobScheduler(@NonNull JobLauncher jobLauncher,
                                     @NonNull Job messageArchiveJob) {
        this.jobLauncher = Objects.requireNonNull(jobLauncher);
        this.messageArchiveJob = Objects.requireNonNull(messageArchiveJob);
    }

    /**
     * 毎日深夜2時に実行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void runArchiveJob() {
        try {
            logger.info("Starting message archive job");
            
            JobParameters params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
            
            jobLauncher.run(messageArchiveJob, params);
            
            logger.info("Message archive job completed successfully");
        } catch (Exception e) {
            logger.error("Message archive job failed", e);
        }
    }
}
```

**学習ポイント**:
- ✅ **@Scheduled**: 定期実行
- ✅ **Cron式**: `0 0 2 * * ?` = 毎日深夜2時
- ✅ **JobParameters**: ジョブの一意性を保証（同じパラメータでは再実行不可）

**Cron式の説明**:
```
0  0  2  *  *  ?
秒 分 時 日 月 曜日

0 0 2 * * ?   = 毎日2時
0 */10 * * * ? = 10分ごと
0 0 9-17 * * MON-FRI = 平日9-17時の毎正時
```

---

## 7. Step 5: 手動実行とテスト

### 7.1 手動実行用コントローラー

**ファイル**: `src/main/java/com/minislack/presentation/api/admin/BatchJobController.java`

```java
package com.minislack.presentation.api.admin;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * バッチジョブ手動実行用コントローラー
 * 開発・テスト用
 */
@RestController
@RequestMapping("/api/v1/admin/batch")
public class BatchJobController {
    
    private static final Logger logger = LoggerFactory.getLogger(BatchJobController.class);
    
    private final JobLauncher jobLauncher;
    private final Job messageArchiveJob;

    public BatchJobController(@NonNull JobLauncher jobLauncher,
                             @NonNull Job messageArchiveJob) {
        this.jobLauncher = Objects.requireNonNull(jobLauncher);
        this.messageArchiveJob = Objects.requireNonNull(messageArchiveJob);
    }

    /**
     * メッセージアーカイブジョブを手動実行
     * POST /api/v1/admin/batch/archive-messages
     */
    @PostMapping("/archive-messages")
    @NonNull
    public ResponseEntity<String> runArchiveJob() {
        try {
            logger.info("Manually triggering message archive job");
            
            JobParameters params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
            
            jobLauncher.run(messageArchiveJob, params);
            
            return ResponseEntity.ok("Message archive job started successfully");
        } catch (Exception e) {
            logger.error("Failed to start message archive job", e);
            return ResponseEntity.internalServerError()
                .body("Failed to start job: " + e.getMessage());
        }
    }
}
```

### 7.2 テストデータの準備

```bash
# 古い日付のメッセージを作成するために、SQLで直接挿入
docker exec -it minislack-postgres psql -U minislack -d minislack

# 100日前のメッセージを挿入
INSERT INTO messages (message_id, channel_id, user_id, content, created_at)
VALUES (
  gen_random_uuid()::text,
  '<channel-id>',
  '<user-id>',
  'This message is 100 days old',
  NOW() - INTERVAL '100 days'
);
```

### 7.3 バッチジョブ実行

```bash
curl -X POST http://localhost:8080/api/v1/admin/batch/archive-messages
```

**アプリケーションログ確認**:
```
Starting message archive job
Job: [SimpleJob: [name=messageArchiveJob]] launched
Executing step: [archiveStep]
Step: [archiveStep] executed in 1s234ms
Message archive job completed successfully
```

### 7.4 アーカイブ確認

```sql
-- アーカイブテーブルを確認
SELECT 
    archive_id,
    message_id,
    content,
    created_at,
    archived_at
FROM message_archives;

-- 元のテーブルからは削除されているか確認
SELECT COUNT(*) FROM messages WHERE created_at < NOW() - INTERVAL '90 days';
-- 結果: 0
```

---

## 8. 統計集計ジョブの実装

### 8.1 ActivityStatsJpaEntity

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/stats/ActivityStatsJpaEntity.java`

```java
package com.minislack.infrastructure.persistence.stats;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 活動統計JPAエンティティ
 */
@Entity
@Table(name = "activity_stats")
public class ActivityStatsJpaEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long statsId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "channel_id", length = 36)
    private String channelId;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "message_count", nullable = false)
    private long messageCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ActivityStatsJpaEntity() {
    }

    // Getter / Setter
    public Long getStatsId() { return statsId; }
    public void setStatsId(Long statsId) { this.statsId = statsId; }

    @NonNull
    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(@NonNull LocalDate targetDate) { this.targetDate = targetDate; }

    @Nullable
    public String getChannelId() { return channelId; }
    public void setChannelId(@Nullable String channelId) { this.channelId = channelId; }

    @Nullable
    public String getUserId() { return userId; }
    public void setUserId(@Nullable String userId) { this.userId = userId; }

    public long getMessageCount() { return messageCount; }
    public void setMessageCount(long messageCount) { this.messageCount = messageCount; }

    @NonNull
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(@NonNull LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

### 8.2 統計集計用のRepository

**ファイル**: `src/main/java/com/minislack/infrastructure/persistence/stats/SpringDataActivityStatsRepository.java`

```java
package com.minislack.infrastructure.persistence.stats;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataActivityStatsRepository extends JpaRepository<ActivityStatsJpaEntity, Long> {
}
```

### 8.3 統計集計Tasklet

**ファイル**: `src/main/java/com/minislack/infrastructure/batch/DailyStatsTasklet.java`

```java
package com.minislack.infrastructure.batch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.minislack.infrastructure.persistence.message.MessageJpaEntity;
import com.minislack.infrastructure.persistence.message.SpringDataMessageRepository;
import com.minislack.infrastructure.persistence.stats.ActivityStatsJpaEntity;
import com.minislack.infrastructure.persistence.stats.SpringDataActivityStatsRepository;

/**
 * 日次統計集計Tasklet
 */
@Component
public class DailyStatsTasklet implements Tasklet {
    
    private static final Logger logger = LoggerFactory.getLogger(DailyStatsTasklet.class);
    
    private final SpringDataMessageRepository messageRepository;
    private final SpringDataActivityStatsRepository statsRepository;

    public DailyStatsTasklet(@NonNull SpringDataMessageRepository messageRepository,
                            @NonNull SpringDataActivityStatsRepository statsRepository) {
        this.messageRepository = Objects.requireNonNull(messageRepository);
        this.statsRepository = Objects.requireNonNull(statsRepository);
    }

    @Override
    @NonNull
    public RepeatStatus execute(@NonNull StepContribution contribution, 
                               @NonNull ChunkContext chunkContext) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime startOfDay = yesterday.atStartOfDay();
        LocalDateTime endOfDay = yesterday.atTime(23, 59, 59);
        
        logger.info("Collecting stats for date: {}", yesterday);
        
        // 昨日のメッセージを取得
        List<MessageJpaEntity> messages = messageRepository
            .findByCreatedAtBetween(startOfDay, endOfDay);
        
        // チャンネル別集計
        Map<String, Long> channelStats = messages.stream()
            .collect(Collectors.groupingBy(
                MessageJpaEntity::getChannelId,
                Collectors.counting()
            ));
        
        channelStats.forEach((channelId, count) -> {
            ActivityStatsJpaEntity stats = new ActivityStatsJpaEntity();
            stats.setTargetDate(yesterday);
            stats.setChannelId(channelId);
            stats.setMessageCount(count);
            stats.setCreatedAt(LocalDateTime.now());
            statsRepository.save(stats);
        });
        
        // ユーザー別集計
        Map<String, Long> userStats = messages.stream()
            .collect(Collectors.groupingBy(
                MessageJpaEntity::getUserId,
                Collectors.counting()
            ));
        
        userStats.forEach((userId, count) -> {
            ActivityStatsJpaEntity stats = new ActivityStatsJpaEntity();
            stats.setTargetDate(yesterday);
            stats.setUserId(userId);
            stats.setMessageCount(count);
            stats.setCreatedAt(LocalDateTime.now());
            statsRepository.save(stats);
        });
        
        logger.info("Stats collection completed. Channels: {}, Users: {}", 
            channelStats.size(), userStats.size());
        
        return RepeatStatus.FINISHED;
    }
}
```

**Spring Data JPA Repositoryメソッド追加**:

`SpringDataMessageRepository`に追加：
```java
@NonNull
List<MessageJpaEntity> findByCreatedAtBetween(@NonNull LocalDateTime start, @NonNull LocalDateTime end);
```

**学習ポイント**:
- ✅ **Tasklet**: 単純な処理（集計等）に適している
- ✅ **Stream API**: グループ化と集計
- ✅ **RepeatStatus.FINISHED**: 処理完了を返却

---

## 9. 動作確認

### 9.1 バッチジョブの手動実行

```bash
# アーカイブジョブ
curl -X POST http://localhost:8080/api/v1/admin/batch/archive-messages

# 統計集計ジョブ（追加実装後）
curl -X POST http://localhost:8080/api/v1/admin/batch/collect-stats
```

### 9.2 バッチ実行履歴の確認

Spring Batchは実行履歴を自動的にDBに保存します：

```sql
-- ジョブ実行履歴
SELECT 
    job_instance_id,
    job_name,
    job_key
FROM batch_job_instance
ORDER BY job_instance_id DESC;

-- ジョブ実行結果
SELECT 
    job_execution_id,
    job_instance_id,
    status,
    start_time,
    end_time
FROM batch_job_execution
ORDER BY job_execution_id DESC;

-- ステップ実行結果
SELECT 
    step_execution_id,
    step_name,
    status,
    read_count,
    write_count,
    commit_count,
    rollback_count
FROM batch_step_execution
ORDER BY step_execution_id DESC;
```

### 9.3 アーカイブ結果確認

```sql
-- アーカイブされたメッセージ数
SELECT COUNT(*) FROM message_archives;

-- アーカイブ日時別の集計
SELECT 
    DATE(archived_at) as archive_date,
    COUNT(*) as archived_count
FROM message_archives
GROUP BY DATE(archived_at)
ORDER BY archive_date DESC;
```

---

## 10. まとめ

### 10.1 実装したもの

- ✅ メッセージアーカイブジョブ
  - ItemReader, ItemProcessor, ItemWriter
  - Chunk指向処理
- ✅ 統計集計ジョブ（Tasklet）
- ✅ スケジューラー設定
- ✅ 手動実行API

### 10.2 学んだこと

- ✅ **Spring Batch**: Job, Step, Chunk処理
- ✅ **ItemReader/Processor/Writer**: データ処理パイプライン
- ✅ **Tasklet**: シンプルな処理の実装
- ✅ **@Scheduled**: 定期実行
- ✅ **Cron式**: スケジュール定義

### 10.3 次のステップ

バッチ処理が完成しました！次はWeb UIを実装します：

- [15-web-ui.md](15-web-ui.md) - Web UI実装

---

## 11. よくある質問

### Q1. Chunkサイズはどう決めるのか？

**A**: 
- **小さい（10-100）**: メモリ使用量少、トランザクション頻繁
- **大きい（1000-10000）**: パフォーマンス高、メモリ使用量多

一般的には**100-500**が推奨されます。

### Q2. ジョブが失敗したら？

**A**: Spring Batchは自動的にリトライ可能です。また、失敗箇所から再開もできます。

### Q3. 本番環境でのバッチ実行は？

**A**: 
- Kubernetes CronJob
- AWS Batch
- Quartz Scheduler
などを使用します。

---

## 12. 参考資料

- [Spring Batch](https://spring.io/projects/spring-batch)
- [Cron Expression](https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html)
- [Spring @Scheduled](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)

