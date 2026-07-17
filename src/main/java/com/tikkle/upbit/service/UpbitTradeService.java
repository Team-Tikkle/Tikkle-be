package com.tikkle.upbit.service;

import com.tikkle.upbit.client.UpbitOrderClient;
import com.tikkle.upbit.dto.response.UpbitOrderResponse;
import com.tikkle.upbit.exception.UpbitInvalidKeyException;
import com.tikkle.user.entity.LinkedAccount;
import com.tikkle.user.exception.LinkedAccountNotFoundException;
import com.tikkle.user.repository.LinkedAccountRepository;
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

    private static final BigDecimal FEE_MULTIPLIER = new BigDecimal("1.0005");
    private static final int EXECUTION_POLL_ATTEMPTS = 10;
    private static final long EXECUTION_POLL_INTERVAL_MS = 500L;

    private final UpbitOrderClient upbitOrderClient;
    private final LinkedAccountRepository linkedAccountRepository;
    private final UpbitPortfolioUpdater portfolioUpdater;

    /**
     * 트레이딩 체결 결과를 담는 레코드
     */
    public record TradeResult(BigDecimal executedPrice, BigDecimal executedVolume, String tradeUuid, boolean isPending) {}

    /**
     * 사용자의 업비트 계좌 정보를 조회한 뒤 지정된 코인(마켓)에 대해 시장가 매수를 수행합니다.
     * 체결이 완료될 때까지 일정 횟수 폴링하며, 완료 후 사용자의 포트폴리오 원장을 업데이트합니다.
     *
     * <p>주문이 업비트에 접수된 뒤로는 어떤 실패가 나더라도 예외를 던지지 않고
     * {@code isPending=true}로 반환합니다. 예외를 던지면 이미 체결됐거나 체결될 주문의
     * uuid를 잃어버려 사용자의 코인이 유실되기 때문이며, 이후 추적은 매수 폴링 스케줄러가 맡습니다.
     *
     * @param userId 매수를 수행할 사용자 ID
     * @param market 매수할 마켓 (예: KRW-BTC)
     * @param krwAmount 매수할 원화 금액
     * @param identifier 주문 멱등 식별자 (결제 이벤트당 고유)
     * @return 체결 결과 (평단가, 수량, 주문UUID, 지연여부)
     * @throws LinkedAccountNotFoundException 연동된 계좌가 없는 경우
     */
    public TradeResult executeTrade(Long userId, String market, int krwAmount, String identifier) {
        log.info("[UpbitTradeService] 시장가 매수 주문 시작 - userId: {}, market: {}, krwAmount: {}, identifier: {}",
                userId, market, krwAmount, identifier);

        LinkedAccount linkedAccount = linkedAccountRepository.findByUserId(userId)
                .orElseThrow(LinkedAccountNotFoundException::new);

        String accessKey = linkedAccount.getUpbitAccessKey();
        String secretKey = linkedAccount.getUpbitSecretKey();

        // 수수료(0.05%) 역산: 수수료를 포함한 총 출금액이 사용자의 잔돈(krwAmount)을 초과하지 않도록 보정
        int orderAmount = BigDecimal.valueOf(krwAmount)
                .divide(FEE_MULTIPLIER, 0, RoundingMode.FLOOR)
                .intValue();

        String uuid = placeOrderIdempotently(market, orderAmount, accessKey, secretKey, identifier);

        // 이 지점부터 주문은 업비트에 실재한다. uuid를 잃으면 코인이 유실되므로 예외를 밖으로 내보내지 않는다.
        try {
            return awaitExecution(userId, market, uuid, accessKey, secretKey);
        } catch (Exception e) {
            log.error("[UpbitTradeService] 주문 접수 후 체결 확인 실패, 비동기 추적으로 전환 - uuid: {}", uuid, e);
            return new TradeResult(null, null, uuid, true);
        }
    }

    /**
     * identifier를 멱등키로 사용해 시장가 매수 주문을 접수하고 주문 uuid를 반환합니다.
     * 주문 요청이 실패하면 응답만 유실됐거나 동일 identifier 재요청이 거부된 경우일 수 있으므로,
     * identifier로 재조회해 실제 접수 여부를 확인한 뒤에만 실패로 확정합니다.
     */
    private String placeOrderIdempotently(String market, int orderAmount, String accessKey, String secretKey, String identifier) {
        try {
            return upbitOrderClient.placeMarketBuyOrder(market, orderAmount, accessKey, secretKey, identifier).uuid();
        } catch (UpbitInvalidKeyException e) {
            // 키 문제면 identifier 재조회도 같은 이유로 실패하므로 즉시 전파한다
            throw e;
        } catch (Exception e) {
            UpbitOrderResponse existing = upbitOrderClient.findOrderByIdentifier(identifier, accessKey, secretKey);
            if (existing == null || existing.uuid() == null) {
                throw e;
            }
            log.warn("[UpbitTradeService] 주문 요청은 실패했으나 identifier로 접수된 주문을 확인, 해당 주문을 이어서 추적 - identifier: {}, uuid: {}",
                    identifier, existing.uuid(), e);
            return existing.uuid();
        }
    }

    /**
     * 접수된 주문의 체결을 최대 {@value #EXECUTION_POLL_ATTEMPTS}회 폴링하며 기다립니다.
     * 시간 내에 체결이 확인되면 원장까지 반영하고, 확인되지 않으면 비동기 추적 대상으로 넘깁니다.
     */
    private TradeResult awaitExecution(Long userId, String market, String uuid, String accessKey, String secretKey) {
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalFunds = BigDecimal.ZERO;

        for (int i = 0; i < EXECUTION_POLL_ATTEMPTS; i++) {
            sleep();

            UpbitOrderResponse orderDetails = upbitOrderClient.getOrderDetails(uuid, accessKey, secretKey);
            List<UpbitOrderResponse.UpbitTrade> trades = orderDetails.trades();
            String state = orderDetails.state();

            // 시장가 매수는 부분 체결 후 잔량이 cancel 되는 것이 정상 흐름이므로 done/cancel 모두 체결로 본다
            if (trades != null && !trades.isEmpty() && ("done".equals(state) || "cancel".equals(state))) {
                for (UpbitOrderResponse.UpbitTrade trade : trades) {
                    totalVolume = totalVolume.add(new BigDecimal(trade.volume()));
                    totalFunds = totalFunds.add(new BigDecimal(trade.funds()));
                }
                break;
            }
        }

        if (totalVolume.compareTo(BigDecimal.ZERO) == 0) {
            log.info("[UpbitTradeService] 5초 내 체결 실패, 비동기 추적으로 전환 - uuid: {}", uuid);
            return new TradeResult(null, null, uuid, true);
        }

        BigDecimal averagePrice = totalFunds.divide(totalVolume, 4, RoundingMode.HALF_UP);
        TradeResult result = new TradeResult(averagePrice, totalVolume, uuid, false);

        portfolioUpdater.updatePortfolio(userId, market, result);

        return result;
    }

    private void sleep() {
        try {
            Thread.sleep(EXECUTION_POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
