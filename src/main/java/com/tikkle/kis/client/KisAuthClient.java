package com.tikkle.kis.client;

import com.tikkle.kis.dto.request.KisTokenRequest;
import com.tikkle.kis.dto.response.KisTokenResponse;
import com.tikkle.kis.exception.KisTokenIssueFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * KIS OAuth 인증 클라이언트.
 * Cache-Aside 패턴으로 Redis에 토큰을 캐싱하여 Rate Limit을 방어합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KisAuthClient {
    private final RestClient kisRestClient;
    private final StringRedisTemplate redisTemplate;

    private static final String TOKEN_CACHE_PREFIX = "kis:token:";
    // KIS 토큰 만료시간(약 24시간)보다 약간 짧게 설정하여 안전 마진 확보
    private static final Duration TOKEN_TTL = Duration.ofHours(23);

    /**
     * 유저의 KIS Access Token을 반환합니다.
     * Redis 캐시에 있으면 즉시 반환, 없으면 KIS API를 호출하여 발급 후 캐싱합니다.
     */
    public String getAccessToken(Long userId, String appKey, String appSecret) {
        String cacheKey = TOKEN_CACHE_PREFIX + userId;

        // 1. Cache HIT → 즉시 반환
        String cachedToken = redisTemplate.opsForValue().get(cacheKey);
        if (cachedToken != null) {
            log.debug("[KIS Auth] Token cache HIT for userId={}", userId);
            return cachedToken;
        }

        // 2. Cache MISS → KIS API 호출 + 캐싱
        log.info("[KIS Auth] Token cache MISS for userId={}. Issuing new token.", userId);
        String accessToken = issueTokenWithRetry(appKey, appSecret);

        redisTemplate.opsForValue().set(cacheKey, accessToken, TOKEN_TTL);
        return accessToken;
    }

    /**
     * 최대 3회 재시도 + 지수 백오프(Exponential Backoff)로 KIS 토큰을 발급합니다.
     */
    private String issueTokenWithRetry(String appKey, String appSecret) {
        int maxRetries = 3;
        long baseDelayMs = 200;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                KisTokenResponse response = kisRestClient.post()
                        .uri("/oauth2/tokenP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(KisTokenRequest.of(appKey, appSecret))
                        .retrieve()
                        .body(KisTokenResponse.class);

                if (response != null && response.accessToken() != null) {
                    log.info("[KIS Auth] Token issued successfully.");
                    return response.accessToken();
                }
                log.warn("[KIS Auth] Token response is null or empty. Attempt {}/{}", attempt, maxRetries);
            } catch (Exception e) {
                log.warn("[KIS Auth] Token issue attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    throw new KisTokenIssueFailedException();
                }
                // 지수 백오프: 200ms, 400ms, 800ms
                sleep(baseDelayMs * (1L << (attempt - 1)));
            }
        }
        throw new KisTokenIssueFailedException();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}