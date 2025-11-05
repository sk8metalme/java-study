package com.minislack.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * テスト用リポジトリ
 * PostgreSQL接続確認用
 */
@Repository
public interface TestRepository extends JpaRepository<TestEntity, Long> {
}
