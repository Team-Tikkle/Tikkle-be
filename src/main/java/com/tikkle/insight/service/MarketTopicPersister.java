package com.tikkle.insight.service;

import com.tikkle.insight.entity.MarketTopic;
import com.tikkle.insight.fetcher.FetchedNewsItem;
import com.tikkle.insight.repository.MarketTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 수집된 마켓 토픽의 DB 저장/정리/캐시 evict를 트랜잭션 안에서 담당한다.
 * 네트워크 I/O와 분리하기 위해 {@link MarketTopicCollectService}에서 별도 빈으로 호출된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketTopicPersister {
    private static final int RETENTION_DAYS = 7;
    private static final int MAX_RECORDS = 100;

    private final MarketTopicRepository marketTopicRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public void persist(Map<String, FetchedNewsItem> deduped) {
        // 1. 신규 link만 저장
        int saved = 0;
        for (FetchedNewsItem item : deduped.values()) {
            if (!marketTopicRepository.existsByLink(item.link())) {
                marketTopicRepository.save(toEntity(item));
                saved++;
            }
        }

        // 2. 오래된 데이터 정리 (7일 초과 + MAX_RECORDS 초과분)
        long deletedByAge = marketTopicRepository.deleteByFetchedAtBefore(
                LocalDateTime.now().minusDays(RETENTION_DAYS));
        int deletedByLimit = 0;
        if (marketTopicRepository.count() > MAX_RECORDS) {
            List<Long> keepIds = marketTopicRepository.findIdsOrderByPublishedAtDesc(
                    PageRequest.of(0, MAX_RECORDS));
            deletedByLimit = marketTopicRepository.deleteByIdNotIn(keepIds);
        }

        // 3. 캐시 evict (다음 조회 시 DB에서 갱신). Redis 장애가 DB 트랜잭션을 롤백시키지 않도록 격리.
        evictCache();

        log.info("마켓 토픽 저장 완료. 수집={}, 신규저장={}, 기간초과삭제={}, 건수초과삭제={}",
                deduped.size(), saved, deletedByAge, deletedByLimit);
    }

    private void evictCache() {
        try {
            redisTemplate.delete(InsightService.MARKET_TOPICS_CACHE_KEY);
        } catch (DataAccessException e) {
            log.warn("마켓 토픽 캐시 evict 실패. reason={}", e.getMessage());
        }
    }

    private MarketTopic toEntity(FetchedNewsItem item) {
        return MarketTopic.builder()
                .title(item.title())
                .press(item.press())
                .link(item.link())
                .summary(item.summary())
                .thumbnailUrl(item.thumbnailUrl())
                .publishedAt(item.publishedAt())
                .keyword(item.keyword())
                .build();
    }
}