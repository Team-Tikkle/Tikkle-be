package com.tikkle.upbit.service;

import com.tikkle.investment.entity.Portfolio;
import com.tikkle.investment.repository.PortfolioRepository;
import com.tikkle.upbit.client.UpbitOrderClient;
import com.tikkle.upbit.dto.response.UpbitOrderResponse;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.entity.User;
import com.tikkle.user.exception.UserNotFoundException;
import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tikkle.global.exception.CustomException;
import com.tikkle.global.exception.ErrorCode;
import com.tikkle.upbit.exception.UpbitOrderExecutionFailedException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpbitTradeService {

    private final UpbitOrderClient upbitOrderClient;
    private final LinkedAccountRepository linkedAccountRepository;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;

    public record TradeResult(BigDecimal executedPrice, BigDecimal executedVolume) {}

    @Transactional
    public TradeResult executeTrade(Long userId, String market, int krwAmount) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        LinkedAccount linkedAccount = linkedAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)); // TODO: specific exception if needed

        String accessKey = linkedAccount.getUpbitAccessKey();
        String secretKey = linkedAccount.getUpbitSecretKey();

        // 1. 수수료(0.05%) 역산: 수수료를 포함한 총 출금액이 사용자의 잔돈(krwAmount)을 초과하지 않도록 보정 (소수점 버림)
        int orderAmount = (int) Math.floor(krwAmount / 1.0005);

        // 2. 시장가 매수 주문
        UpbitOrderResponse orderResponse = upbitOrderClient.placeMarketBuyOrder(market, orderAmount, accessKey, secretKey);
        String uuid = orderResponse.uuid();

        // 2. 체결 대기 (500ms)
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 3. 체결 확인 및 가중 평균 계산 (최대 3회 폴링)
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalFunds = BigDecimal.ZERO;
        boolean isExecuted = false;

        for (int i = 0; i < 3; i++) {
            UpbitOrderResponse orderDetails = upbitOrderClient.getOrderDetails(uuid, accessKey, secretKey);
            List<UpbitOrderResponse.UpbitTrade> trades = orderDetails.trades();

            if (trades != null && !trades.isEmpty()) {
                isExecuted = true;
                for (UpbitOrderResponse.UpbitTrade trade : trades) {
                    BigDecimal volume = new BigDecimal(trade.volume());
                    BigDecimal funds = new BigDecimal(trade.funds());
                    
                    totalVolume = totalVolume.add(volume);
                    totalFunds = totalFunds.add(funds);
                }
                break; // 체결 완료되면 폴링 중단
            }

            try {
                Thread.sleep(500); // 실패 시 500ms 대기 후 재시도
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (!isExecuted || totalVolume.compareTo(BigDecimal.ZERO) == 0) {
            throw new UpbitOrderExecutionFailedException();
        }

        // 가중 평균 평단가 산출 = 총 체결 금액 / 총 체결 수량
        BigDecimal averagePrice = totalFunds.divide(totalVolume, 4, RoundingMode.HALF_UP);

        // 4. 원장 업데이트 (Portfolio Upsert)
        Portfolio portfolio = portfolioRepository.findByUserIdAndMarket(userId, market)
                .orElseGet(() -> portfolioRepository.save(Portfolio.builder()
                        .user(user)
                        .market(market)
                        .quantity(BigDecimal.ZERO)
                        .averagePrice(BigDecimal.ZERO)
                        .build()));

        portfolio.updateHolding(averagePrice, totalVolume);

        return new TradeResult(averagePrice, totalVolume);
    }
}
