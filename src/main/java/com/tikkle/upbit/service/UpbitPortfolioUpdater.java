package com.tikkle.upbit.service;

import com.tikkle.investment.entity.Portfolio;
import com.tikkle.investment.repository.PortfolioRepository;
import com.tikkle.user.entity.User;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 트레이딩 체결 결과를 사용자의 투자 포트폴리오 원장에 반영하는 책임을 갖는 컴포넌트입니다.
 * UpbitTradeService 에서 트랜잭션 분리를 위해 호출됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitPortfolioUpdater {
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;

    /**
     * 트레이딩 결과(매수 체결 내역)를 사용자의 투자 포트폴리오 원장에 반영합니다.
     *
     * @param userId 사용자 ID
     * @param market 체결된 마켓 (코인)
     * @param result 체결 내역 (가격 및 수량)
     */
    @Transactional
    public void updatePortfolio(Long userId, String market, UpbitTradeService.TradeResult result) {
        log.info("[UpbitPortfolioUpdater] 트레이딩 결과 포트폴리오(원장) 반영 시작 - userId: {}, market: {}", userId, market);
        User user = userRepository.getReferenceById(userId);

        Portfolio portfolio = portfolioRepository.findByUserIdAndMarket(userId, market)
                .orElseGet(() -> portfolioRepository.save(Portfolio.builder()
                        .user(user)
                        .market(market)
                        .quantity(BigDecimal.ZERO)
                        .averagePrice(BigDecimal.ZERO)
                        .build()));

        portfolio.updateHolding(result.executedPrice(), result.executedVolume());
        log.info("[UpbitPortfolioUpdater] 포트폴리오(원장) 반영 완료 - userId: {}, market: {}", userId, market);
    }
}