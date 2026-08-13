package com.tikkle.settings.service;

import com.tikkle.settings.entity.CategorySpareChangeRule;
import com.tikkle.settings.repository.CategorySpareChangeRuleRepository;
import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.user.exception.LinkedAccountNotFoundException;
import com.tikkle.user.exception.NoCategoryRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis에서 사용자 설정을 조회하고, 캐시 미스 시 DB에서 복구하는 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsCacheService {
    private final LinkedAccountRepository linkedAccountRepository;
    private final CategorySpareChangeRuleRepository categorySpareChangeRuleRepository;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Redis에서 유저 설정을 조회하고, 캐시 미스 시 DB에서 복구한다.
     */
    public Map<String, String> getUserSettings(Long userId) {
        String redisKey = SettingsCacheManager.USER_SETTINGS_CACHE_PREFIX + userId;
        Map<Object, Object> rawSettings = redisTemplate.opsForHash().entries(redisKey);

        if (rawSettings != null && !rawSettings.isEmpty()) {
            Map<String, String> settings = new HashMap<>();
            rawSettings.forEach((k, v) -> settings.put(String.valueOf(k), String.valueOf(v)));
            return settings;
        }

        log.warn("[SettingsCacheService] Redis 캐시 미스 발생 (DB 복구 진행) - userId: {}", userId);

        var accountOpt = linkedAccountRepository.findByUserId(userId);
        if (accountOpt.isEmpty()) {
            log.error("[SettingsCacheService] 연동 계좌(카드) 정보 미존재 - userId: {}", userId);
            throw new LinkedAccountNotFoundException();
        }

        var account = accountOpt.get();
        List<CategorySpareChangeRule> rules = categorySpareChangeRuleRepository.findByUserId(userId);
        if (rules == null || rules.isEmpty()) {
            log.error("[SettingsCacheService] 카테고리 잔돈 규칙 미존재 - userId: {}", userId);
            throw new NoCategoryRuleException();
        }

        Map<String, String> cacheData = new HashMap<>();
        // 타겟 카드 미등록 상태면 필드를 넣지 않는다(결제 시 타겟 카드 불일치로 처리됨)
        if (account.getTargetCardCompany() != null) {
            cacheData.put("targetCardCompany", account.getTargetCardCompany());
        }
        if (account.getTargetCardLast4() != null) {
            cacheData.put("targetCardLast4", account.getTargetCardLast4());
        }
        cacheData.put("isInvestmentEnabled", String.valueOf(account.isInvestmentEnabled()));
        rules.forEach(rule -> {
            if (rule.getCategory() != null) {
                cacheData.put(rule.getCategory().name(), rule.getRuleType().name());
            }
        });

        redisTemplate.opsForHash().putAll(redisKey, cacheData);

        return new HashMap<>(cacheData);
    }
}