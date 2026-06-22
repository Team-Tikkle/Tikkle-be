package com.tikkle.investment.scheduler;

import com.tikkle.investment.entity.Coin;
import com.tikkle.investment.repository.CoinRepository;
import com.tikkle.upbit.client.UpbitMarketClient;
import com.tikkle.upbit.dto.response.UpbitMarketResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoinSyncScheduler {
    private final UpbitMarketClient upbitMarketClient;
    private final CoinRepository coinRepository;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul") // 매일 새벽 4시 실행
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void syncCoinMetadata() {
        log.info("업비트 마켓 리스트 동기화 스케줄러 시작");
        try {
            List<UpbitMarketResponse> markets = upbitMarketClient.getAllMarkets();
            
            // 기존 DB 데이터 맵핑 (업데이트 처리용)
            Map<String, Coin> existingCoins = coinRepository.findAll().stream()
                    .collect(Collectors.toMap(Coin::getMarket, c -> c));

            int insertCount = 0;
            int updateCount = 0;

            for (UpbitMarketResponse marketResponse : markets) {
                // KRW 마켓만 필터링 (필요하다면 조건 수정 가능하지만 현재는 전체 적재)
                Coin existing = existingCoins.get(marketResponse.market());
                
                if (existing != null) {
                    if (!existing.getKoreanName().equals(marketResponse.koreanName()) || 
                        !existing.getEnglishName().equals(marketResponse.englishName())) {
                        existing.updateNames(marketResponse.koreanName(), marketResponse.englishName());
                        updateCount++;
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

            log.info("업비트 마켓 리스트 동기화 완료 - 신규: {}건, 업데이트: {}건", insertCount, updateCount);
        } catch (Exception e) {
            log.error("업비트 마켓 리스트 동기화 실패", e);
        }
    }
}