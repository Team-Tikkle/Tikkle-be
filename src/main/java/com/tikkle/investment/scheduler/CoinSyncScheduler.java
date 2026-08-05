package com.tikkle.investment.scheduler;

import com.tikkle.investment.entity.Coin;
import com.tikkle.investment.exception.CoinSyncFailedException;
import com.tikkle.investment.repository.CoinRepository;
import com.tikkle.upbit.client.UpbitMarketClient;
import com.tikkle.upbit.dto.response.UpbitMarketResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 매일 새벽 업비트 API를 호출하여 최신 마켓(코인) 리스트를 DB와 동기화하는 스케줄러 컴포넌트입니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoinSyncScheduler {
    private final UpbitMarketClient upbitMarketClient;
    private final CoinRepository coinRepository;

    /**
     * 매일 새벽 4시 및 서버 기동 시 업비트 마켓 리스트를 가져와 DB에 코인 메타데이터(한글/영문명 등)를 갱신합니다.
     * 업비트 응답에서 사라진 코인(상장폐지)은 비활성화하여 시세 조회 대상에서 제외합니다.
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul") // 매일 새벽 4시 실행
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void syncCoinMetadata() {
        log.info("[CoinSyncScheduler] 업비트 마켓 리스트 동기화 스케줄러 시작");
        try {
            List<UpbitMarketResponse> markets = upbitMarketClient.getAllMarkets();
            if (markets.isEmpty()) {
                // 빈 응답을 정상으로 취급하면 전체 코인이 비활성화되므로 방어한다
                log.error("[CoinSyncScheduler] 업비트 마켓 리스트가 비어 있어 동기화를 중단합니다");
                throw new CoinSyncFailedException();
            }

            // 기존 DB 데이터 맵핑 (업데이트 처리용)
            Map<String, Coin> existingCoins = coinRepository.findAll().stream()
                    .collect(Collectors.toMap(Coin::getMarket, c -> c));

            int insertCount = 0;
            int updateCount = 0;
            int reactivateCount = 0;

            for (UpbitMarketResponse marketResponse : markets) {
                // KRW 마켓만 필터링 (필요하다면 조건 수정 가능하지만 현재는 전체 적재)
                Coin existing = existingCoins.get(marketResponse.market());

                if (existing != null) {
                    if (!existing.getKoreanName().equals(marketResponse.koreanName()) ||
                        !existing.getEnglishName().equals(marketResponse.englishName())) {
                        existing.updateNames(marketResponse.koreanName(), marketResponse.englishName());
                        updateCount++;
                    }
                    if (!existing.isActive()) { // 재상장
                        existing.activate();
                        reactivateCount++;
                    }
                } else {
                    Coin newCoin = Coin.builder()
                            .market(marketResponse.market())
                            .koreanName(marketResponse.koreanName())
                            .englishName(marketResponse.englishName())
                            .build();
                    coinRepository.save(newCoin);
                    insertCount++;
                }
            }

            // 업비트 응답에 없는 코인은 상장폐지로 간주하고 비활성화 (원장 FK 보존을 위해 삭제하지 않음)
            Set<String> liveMarkets = markets.stream()
                    .map(UpbitMarketResponse::market)
                    .collect(Collectors.toSet());

            List<String> delistedMarkets = existingCoins.values().stream()
                    .filter(Coin::isActive)
                    .filter(coin -> !liveMarkets.contains(coin.getMarket()))
                    .peek(Coin::deactivate)
                    .map(Coin::getMarket)
                    .toList();

            if (!delistedMarkets.isEmpty()) {
                log.warn("[CoinSyncScheduler] 상장폐지 코인 비활성화 처리 - markets: {}", delistedMarkets);
            }

            log.info("[CoinSyncScheduler] 업비트 마켓 리스트 동기화 완료 - 신규: {}, 업데이트: {}, 재상장: {}, 상장폐지: {}",
                    insertCount, updateCount, reactivateCount, delistedMarkets.size());
        } catch (Exception e) {
            log.error("[CoinSyncScheduler] 업비트 마켓 리스트 동기화 실패", e);
            throw new CoinSyncFailedException();
        }
    }
}