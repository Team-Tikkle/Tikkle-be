package com.tikkle.insight.service;

import com.tikkle.insight.fetcher.FetchedNewsItem;
import com.tikkle.insight.fetcher.NewsFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google News RSS 수집을 조율한다.
 * 외부 HTTP 호출(fetch)은 트랜잭션 밖에서 수행하고, DB 저장/정리/캐시 evict는
 * {@link MarketTopicPersister}의 @Transactional 메서드에 위임한다.
 * (느린 네트워크 I/O 동안 DB 커넥션을 점유하지 않기 위함.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketTopicCollectService {
    /** 시장 단위 광범위 키워드. 개별 종목명은 노이즈가 커 제외. */
    private static final List<String> KEYWORDS = List.of("코스피", "코스닥", "증시", "주식");
    private static final int MAX_LINK_LENGTH = 500;

    private final NewsFetcher newsFetcher;
    private final MarketTopicPersister marketTopicPersister;

    public void collect() {
        // 키워드별 수집(네트워크) 후 link 기준 dedup (먼저 등장한 키워드를 유지)
        Map<String, FetchedNewsItem> deduped = new LinkedHashMap<>();
        for (String keyword : KEYWORDS) {
            for (FetchedNewsItem item : newsFetcher.fetch(keyword)) {
                if (isStorable(item)) {
                    deduped.putIfAbsent(item.link(), item);
                }
            }
        }
        // 저장/정리/캐시 evict (트랜잭션 안)
        marketTopicPersister.persist(deduped);
    }

    private boolean isStorable(FetchedNewsItem item) {
        if (item.title() == null || item.link() == null) {
            return false;
        }
        // link는 UNIQUE VARCHAR(500). 초과하면 insert가 실패하므로 미리 제외.
        return item.link().length() <= MAX_LINK_LENGTH;
    }
}