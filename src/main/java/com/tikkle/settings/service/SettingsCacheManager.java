package com.tikkle.settings.service;

import com.tikkle.investment.entity.enums.ExecutionMode;
import com.tikkle.payment.entity.CategorySpareChangeRule;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 설정 변경을 결제 경로용 Redis 캐시에 반영한다.
 * 온보딩이 warm-up 하는 단일 Hash 키({@code user:settings:{userId}})를 재사용하며,
 * Hash 전체를 덮어쓰지 않고 변경된 필드만 갱신한다(targetCardLast4 등 보존).
 */
@Component
@RequiredArgsConstructor
public class SettingsCacheManager {
    private final RedisTemplate<String, String> redisTemplate;

    private static final String USER_SETTINGS_CACHE_PREFIX = "user:settings:";
    private static final String EXECUTION_MODE_FIELD = "executionMode";

    public void updateExecutionMode(Long userId, ExecutionMode executionMode) {
        redisTemplate.opsForHash().put(cacheKey(userId), EXECUTION_MODE_FIELD, executionMode.name());
    }

    public void updateSpareChangeRules(Long userId, List<CategorySpareChangeRule> rules) {
        String cacheKey = cacheKey(userId);
        rules.forEach(rule ->
                redisTemplate.opsForHash().put(cacheKey, rule.getCategory().name(), rule.getRuleType().name())
        );
    }

    private String cacheKey(Long userId) {
        return USER_SETTINGS_CACHE_PREFIX + userId;
    }
}
