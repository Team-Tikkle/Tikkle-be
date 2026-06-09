package com.tikkle.insight.scheduler;

import com.tikkle.insight.service.MarketTopicCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 마켓 토픽(뉴스) 수집 스케줄러.
 * 비공식 RSS 피드 차단 회피 + 매너를 위해 매일 2회(07시/18시)만 수집한다.
 * 사용자 서빙은 DB/Redis 캐시로 분리되어 있어 수집 주기가 신선도에 큰 영향을 주지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketTopicScheduler {

    private final MarketTopicCollectService marketTopicCollectService;

    @Scheduled(cron = "0 0 7,18 * * *", zone = "Asia/Seoul")
    public void collectMarketTopics() {
        log.info("마켓 토픽 수집 스케줄러 시작");
        marketTopicCollectService.collect();
    }
}