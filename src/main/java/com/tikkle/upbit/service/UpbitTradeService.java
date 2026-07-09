package com.tikkle.upbit.service;

import com.tikkle.upbit.client.UpbitOrderClient;
import com.tikkle.upbit.dto.response.UpbitOrderResponse;
import com.tikkle.upbit.exception.UpbitOrderExecutionFailedException;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.exception.LinkedAccountNotFoundException;
import com.tikkle.user.repository.LinkedAccountRepository;
import com.tikkle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 사용자의 증권 계좌 정보를 이용하여 업비트 시장가 매수를 수행하는 서비스입니다.
 * 외부 트레이딩 호출과 포트폴리오 원장 동기화를 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpbitTradeService {

    private final UpbitOrderClient upbitOrderClient;
    private final LinkedAccountRepository linkedAccountRepository;
    private final UserRepository userRepository;
    private final UpbitPortfolioUpdater portfolioUpdater;

    /**
     * 트레이딩 체결 결과를 담는 레코드
     */
    public record TradeResult(BigDecimal executedPrice, BigDecimal executedVolume) {}

    /**
     * 사용자의 업비트 계좌 정보를 조회한 뒤 지정된 코인(마켓)에 대해 시장가 매수를 수행합니다.
     * 체결이 완료될 때까지 일정 횟수 폴링하며, 완료 후 사용자의 포트폴리오 원장을 업데이트합니다.
     *
     * @param userId 매수를 수행할 사용자 ID
     * @param market 매수할 마켓 (예: KRW-BTC)
     * @param krwAmount 매수할 원화 금액
     * @return 체결 결과 (평균 단가, 체결 수량)
     * @throws LinkedAccountNotFoundException 연동된 계좌가 없는 경우
     * @throws UpbitOrderExecutionFailedException 체결 폴링 대기 시간 초과 또는 실패 시
     */
    public TradeResult executeTrade(Long userId, String market, int krwAmount) {
        log.info("[UpbitTradeService] 시장가 매수 주문 시작 - userId: {}, market: {}, krwAmount: {}", userId, market, krwAmount);
        // 1. 사전 데이터 로드 (I/O 발생 전, 별도 트랜잭션 불필요)
        LinkedAccount linkedAccount = linkedAccountRepository.findByUserId(userId)
                .orElseThrow(LinkedAccountNotFoundException::new);

        String accessKey = linkedAccount.getUpbitAccessKey();
        String secretKey = linkedAccount.getUpbitSecretKey();

        // 2. 수수료(0.05%) 역산: 수수료를 포함한 총 출금액이 사용자의 잔돈(krwAmount)을 초과하지 않도록 보정 (BigDecimal 안전 연산)
        BigDecimal amountBd = BigDecimal.valueOf(krwAmount);
        BigDecimal feeRate = new BigDecimal("1.0005");
        int orderAmount = amountBd.divide(feeRate, 0, RoundingMode.FLOOR).intValue();

        // 3. 시장가 매수 주문 (Blocking I/O)
        UpbitOrderResponse orderResponse = upbitOrderClient.placeMarketBuyOrder(market, orderAmount, accessKey, secretKey);
        String uuid = orderResponse.uuid();

        // 4. 체결 대기 (500ms)
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 5. 체결 확인 및 가중 평균 계산 (최대 3회 폴링)
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalFunds = BigDecimal.ZERO;
        boolean isExecuted = false;

        for (int i = 0; i < 3; i++) {
            UpbitOrderResponse orderDetails = upbitOrderClient.getOrderDetails(uuid, accessKey, secretKey);
            List<UpbitOrderResponse.UpbitTrade> trades = orderDetails.trades();
            String state = orderDetails.state();

            if (trades != null && !trades.isEmpty() && ("done".equals(state) || "cancel".equals(state))) {
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
                Thread.sleep(500); // 실패 또는 부분체결 시 500ms 대기 후 재시도
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (!isExecuted || totalVolume.compareTo(BigDecimal.ZERO) == 0) {
            throw new UpbitOrderExecutionFailedException();
        }

        // 6. 가중 평균 평단가 산출 = 총 체결 금액 / 총 체결 수량
        BigDecimal averagePrice = totalFunds.divide(totalVolume, 4, RoundingMode.HALF_UP);
        TradeResult result = new TradeResult(averagePrice, totalVolume);

        // 7. 트랜잭션 분리된 컴포넌트를 통해 원장 업데이트 호출
        portfolioUpdater.updatePortfolio(userId, market, result);

        return result;
    }
}
