package com.tikkle.settings.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 결제 경로용 사용자 설정 캐시({@code user:settings:{userId}})를 무효화하는 컴포넌트입니다.
 * 설정이 변경되면 캐시를 부분 갱신하지 않고 삭제하며, 이후 첫 결제 시 {@link SettingsCacheService}가 DB에서 재생성합니다.
 */
@Component
@RequiredArgsConstructor
public class SettingsCacheManager {
    private final RedisTemplate<String, String> redisTemplate;

    static final String USER_SETTINGS_CACHE_PREFIX = "user:settings:";

    public void evict(Long userId) {
        redisTemplate.delete(USER_SETTINGS_CACHE_PREFIX + userId);
    }
}
