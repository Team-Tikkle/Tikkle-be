package com.tikkle.insight.service;

import com.tikkle.insight.entity.MarketTopic;
import com.tikkle.insight.fetcher.FetchedNewsItem;
import com.tikkle.insight.fetcher.NewsFetcher;
import com.tikkle.insight.repository.MarketTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google News RSS 수집 → dedup → 저장 → 캐시 evict 를 담당한다.
 * 스케줄러가 호출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketTopicCollectService {

    /** 시장 단위 광범위 키워드. 개별 종목명은 노이즈가 커 제외. */
    private static final List<String> KEYWORDS = List.of("코스피", "코스닥", "증시", "주식");
    private static final int MAX_LINK_LENGTH = 500;
    private static final int RETENTION_DAYS = 7;
    private static final int MAX_RECORDS = 100;

    private final NewsFetcher newsFetcher;
    private final MarketTopicRepository marketTopicRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public void collect() {
        // 1. 키워드별 수집 후 link 기준 dedup (먼저 등장한 키워드를 유지)
        Map<String, FetchedNewsItem> deduped = new LinkedHashMap<>();
        for (String keyword : KEYWORDS) {
            for (FetchedNewsItem item : newsFetcher.fetch(keyword)) {
                if (isStorable(item)) {
                    deduped.putIfAbsent(item.link(), item);
                }
            }
        }

        // 2. 신규 link만 저장
        int saved = 0;
        for (FetchedNewsItem item : deduped.values()) {
            if (!marketTopicRepository.existsByLink(item.link())) {
                marketTopicRepository.save(toEntity(item));
                saved++;
            }
        }

        // 3. 오래된 데이터 정리 (7일 초과 + MAX_RECORDS 초과분)
        long deletedByAge = marketTopicRepository.deleteByFetchedAtBefore(
                LocalDateTime.now().minusDays(RETENTION_DAYS));
        int deletedByLimit = 0;
        if (marketTopicRepository.count() > MAX_RECORDS) {
            List<Long> keepIds = marketTopicRepository.findIdsOrderByPublishedAtDesc(
                    PageRequest.of(0, MAX_RECORDS));
            deletedByLimit = marketTopicRepository.deleteByIdNotIn(keepIds);
        }

        // 4. 캐시 evict (다음 조회 시 DB에서 갱신)
        redisTemplate.delete(InsightService.MARKET_TOPICS_CACHE_KEY);

        log.info("마켓 토픽 수집 완료. 수집={}, 신규저장={}, 기간초과삭제={}, 건수초과삭제={}",
                deduped.size(), saved, deletedByAge, deletedByLimit);
    }

    private boolean isStorable(FetchedNewsItem item) {
        if (item.title() == null || item.link() == null) {
            return false;
        }
        // link는 UNIQUE VARCHAR(500). 초과하면 insert가 실패하므로 미리 제외.
        return item.link().length() <= MAX_LINK_LENGTH;
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