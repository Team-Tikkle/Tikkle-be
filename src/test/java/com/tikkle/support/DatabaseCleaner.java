package com.tikkle.support;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 테스트 사이에 데이터가 남지 않도록 MySQL 테이블과 Redis 를 비웁니다.
 * 기동 시 한 번만 채워지는 시드 테이블과 마이그레이션 기록 테이블은 비우지 않습니다.
 */
@TestComponent
public class DatabaseCleaner {

    // InsightDataSeeder 가 기동 시 채우는 테이블과, Flyway 가 적용한 마이그레이션을 기록하는 테이블.
    // 마이그레이션 기록을 비우면 같은 DB 를 쓰는 다음 기동에서 V1 을 다시 실행하려다 실패한다
    private static final Set<String> KEPT_TABLES = Set.of(
            "INVESTMENT_TERMS", "BEGINNER_ARTICLES", "RECOMMENDED_VIDEOS", "FLYWAY_SCHEMA_HISTORY");

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;

    public DatabaseCleaner(DataSource dataSource, StringRedisTemplate redisTemplate) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
    }

    public void clean() {
        truncateTables();
        flushRedis();
    }

    private void truncateTables() {
        // FOREIGN_KEY_CHECKS 는 세션 단위 설정이라 커넥션 하나에서 끝까지 실행한다
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString(1));
                }
            }

            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : tables) {
                if (!KEPT_TABLES.contains(table.toUpperCase())) {
                    statement.execute("TRUNCATE TABLE `" + table + "`");
                }
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        } catch (SQLException e) {
            throw new IllegalStateException("테스트 데이터 정리에 실패했습니다.", e);
        }
    }

    private void flushRedis() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }
}
