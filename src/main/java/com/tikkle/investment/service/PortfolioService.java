package com.tikkle.investment.service;

import com.tikkle.investment.dto.response.PortfolioResponse;
import com.tikkle.investment.entity.Coin;
import com.tikkle.investment.entity.Portfolio;
import com.tikkle.investment.repository.CoinRepository;
import com.tikkle.investment.repository.PortfolioRepository;
import com.tikkle.upbit.client.UpbitTickerClient;
import com.tikkle.upbit.dto.response.UpbitTickerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 사용자의 보유 포트폴리오 정보를 실시간 업비트 시세와 결합하여 수익률 및 평가금액 등을 계산해주는 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {
    private final PortfolioRepository portfolioRepository;
    private final CoinRepository coinRepository;
    private final UpbitTickerClient upbitTickerClient;

    /**
     * 사용자의 보유 코인 정보와 실시간 업비트 시세를 결합한 포트폴리오 스냅샷을 반환합니다.
     *
     * @param userId 사용자 ID
     * @return 총 평가금액, 원금, 코인별 보유 현황 정보 등을 포함한 포트폴리오 DTO
     */
    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(Long userId) {
        List<Portfolio> portfolios = portfolioRepository.findByUserId(userId);

        // 방어 코드: 보유 코인이 없으면 조기 반환 (업비트 호출 X)
        if (portfolios == null || portfolios.isEmpty()) {
            return new PortfolioResponse(0L, 0L, List.of(), List.of());
        }

        // 마켓 코드 리스트 추출
        List<String> holdingMarketCodes = portfolios.stream()
                .map(Portfolio::getMarket)
                .distinct()
                .collect(Collectors.toList());

        // 업비트 현재가 조회 (방어 코드 추가: API 장애 시 대체)
        String marketsParam = String.join(",", holdingMarketCodes);
        List<UpbitTickerResponse> tickers = new ArrayList<>();
        try {
            tickers = upbitTickerClient.getTickers(marketsParam);
        } catch (Exception e) {
            log.error("[PortfolioService] 업비트 시세 조회 장애 발생. Fallback 로직으로 진행합니다.", e);
            // 에러가 발생해도 빈 리스트를 유지하여 아래 로직이 문제없이 돌아가도록 함
        }
        
        Map<String, BigDecimal> tickerPriceMap = tickers.stream()
                .filter(t -> t.tradePrice() != null)
                .collect(Collectors.toMap(
                        UpbitTickerResponse::market, 
                        UpbitTickerResponse::tradePrice, 
                        (existing, replacement) -> existing));

        // 코인 한글명 매핑용 DB 조회
        Map<String, String> coinNameMap = coinRepository.findAllById(holdingMarketCodes).stream()
                .collect(Collectors.toMap(Coin::getMarket, Coin::getKoreanName));

        long totalPrincipalAmount = 0L;
        long totalEvaluationAmount = 0L;
        List<PortfolioResponse.CoinHoldingDto> holdings = new ArrayList<>();

        for (Portfolio portfolio : portfolios) {
            String market = portfolio.getMarket();
            String coinName = coinNameMap.getOrDefault(market, market); // 미등록 코인이면 마켓코드 자체를 반환
            BigDecimal quantity = portfolio.getQuantity();
            BigDecimal averagePrice = portfolio.getAveragePrice();
            
            // 시세 조회가 안 됐을 경우 임시로 매수 평단가를 현재가로 사용하여 평가 손실 -100% 패닉 방지
            BigDecimal currentPrice = tickerPriceMap.getOrDefault(market, averagePrice);

            // 투자 원금 = 수량 * 평단가 (반올림 후 long 캐스팅)
            long principalAmount = quantity.multiply(averagePrice)
                    .setScale(0, RoundingMode.HALF_UP).longValue();
            
            // 평가 금액 = 수량 * 현재가 (반올림 후 long 캐스팅)
            long evaluationAmount = quantity.multiply(currentPrice)
                    .setScale(0, RoundingMode.HALF_UP).longValue();

            totalPrincipalAmount += principalAmount;
            totalEvaluationAmount += evaluationAmount;

            holdings.add(new PortfolioResponse.CoinHoldingDto(
                    market,
                    coinName,
                    quantity,
                    averagePrice,
                    principalAmount,
                    currentPrice,
                    evaluationAmount
            ));
        }

        return new PortfolioResponse(totalPrincipalAmount, totalEvaluationAmount, holdingMarketCodes, holdings);
    }
}