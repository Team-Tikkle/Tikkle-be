package com.tikkle.insight.service;

import com.tikkle.insight.entity.MarketTopic;
import com.tikkle.insight.fetcher.FetchedNewsItem;
import com.tikkle.insight.repository.MarketTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        // 1. 신규 link만 저장 (existsByLink는 fast-path, save의 UNIQUE 충돌은 try-catch로 방어)
        int saved = 0;
        for (FetchedNewsItem item : deduped.values()) {
            if (!marketTopicRepository.existsByLink(item.link())) {
                try {
                    marketTopicRepository.save(toEntity(item));
                    saved++;
                } catch (DataIntegrityViolationException e) {
                    log.debug("마켓 토픽 중복 저장 건너뜀. link={}", item.link());
                }
            }
        }

        // 2. 오래된 데이터 정리 (7일 초과 + MAX_RECORDS 초과분)
        long deletedByAge = marketTopicRepository.deleteByFetchedAtBefore(
                LocalDateTime.now().minusDays(RETENTION_DAYS));
        int deletedByLimit = 0;
        if (marketTopicRepository.count() > MAX_RECORDS) {
            List<Long> keepIds = marketTopicRepository.findIdsOrderByFetchedAtDesc(
                    PageRequest.of(0, MAX_RECORDS));
            deletedByLimit = marketTopicRepository.deleteByIdNotIn(keepIds);
        }

        // 3. 캐시 evict는 커밋 성공 후에만 실행 (롤백 시 불필요한 evict 방지)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictCache();
            }
        });

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
                .thumbnailUrl(item.thumbnailUrl())
                .publishedAt(item.publishedAt())
                .keyword(item.keyword())
                .build();
    }
}