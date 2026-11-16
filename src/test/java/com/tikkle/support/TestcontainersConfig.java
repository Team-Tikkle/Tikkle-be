package com.tikkle.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 MySQL·Redis 컨테이너 설정입니다.
 * 운영과 같은 계열(MySQL 8.4, Redis 7)을 띄우고 접속 정보를 스프링 설정에 연결합니다.
 * 같은 설정을 쓰는 테스트는 스프링 테스트 컨텍스트 캐시로 컨테이너를 함께 씁니다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    private static final int REDIS_PORT = 6379;

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(REDIS_PORT);
    }
}
