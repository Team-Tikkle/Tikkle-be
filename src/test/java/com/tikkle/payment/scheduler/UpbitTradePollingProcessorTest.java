package com.tikkle.payment.scheduler;

import com.tikkle.investment.entity.Coin;
import com.tikkle.investment.entity.Portfolio;
import com.tikkle.investment.repository.PortfolioRepository;
import com.tikkle.notification.entity.enums.NotificationType;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.support.IntegrationTest;
import com.tikkle.support.UpbitMockServer;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.TwoFactorProvider;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 체결 폴러가 주문 한 건을 어떻게 판정하는지 업비트 대역 서버로 확인합니다.
 * 주문 접수 뒤 지난 시간은 접수 시각을 과거로 돌려서 만듭니다.
 */
class UpbitTradePollingProcessorTest extends IntegrationTest {

    private static final String MARKET = "KRW-BTC";
    private static final String TRADE_UUID = "order-uuid-1";
    private static final String ORDER_PATH = "/v1/order?uuid=" + TRADE_UUID;

    // 타임아웃은 주문 접수 뒤 10분, 확인 재시도 기한은 그 뒤 10분이다
    private static final Duration BEFORE_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration AFTER_TIMEOUT = Duration.ofMinutes(11);
    private static final Duration AFTER_RETRY_LIMIT = Duration.ofMinutes(21);

    private static final String ORDER_DONE_FILLED = """
            {"uuid": "order-uuid-1", "state": "done", "market": "KRW-BTC", "trades": [
              {"market": "KRW-BTC", "uuid": "trade-1", "price": "75000000", "volume": "0.00007", "funds": "5250", "side": "bid"}
            ]}
            """;
    private static final String ORDER_WAIT_UNFILLED = """
            {"uuid": "order-uuid-1", "state": "wait", "market": "KRW-BTC", "trades": []}
            """;
    private static final String ORDER_WAIT_PARTIALLY_FILLED = """
            {"uuid": "order-uuid-1", "state": "wait", "market": "KRW-BTC", "trades": [
              {"market": "KRW-BTC", "uuid": "trade-1", "price": "75000000", "volume": "0.00004", "funds": "3000", "side": "bid"}
            ]}
            """;
    private static final String ORDER_CANCEL_PARTIALLY_FILLED = """
            {"uuid": "order-uuid-1", "state": "cancel", "market": "KRW-BTC", "trades": [
              {"market": "KRW-BTC", "uuid": "trade-1", "price": "75000000", "volume": "0.00004", "funds": "3000", "side": "bid"}
            ]}
            """;
    private static final String ORDER_CANCEL_UNFILLED = """
            {"uuid": "order-uuid-1", "state": "cancel", "market": "KRW-BTC", "trades": []}
            """;
    private static final String CANCEL_ACCEPTED = """
            {"uuid": "order-uuid-1", "state": "wait", "market": "KRW-BTC"}
            """;
    private static final String CANCEL_REJECTED = """
            {"error": {"name": "order_not_found", "message": "주문을 찾지 못했습니다."}}
            """;
    private static final String SERVER_ERROR = """
            {"error": {"name": "server_error", "message": "일시적인 오류입니다."}}
            """;
    private static final String INVALID_KEY = """
            {"error": {"name": "invalid_access_key", "message": "잘못된 엑세스 키입니다."}}
            """;

    @Autowired
    private UpbitTradePollingProcessor tradePollingProcessor;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    private User user;
    private Coin coin;

    @BeforeEach
    void setUp() {
        user = testData.createUser();
        testData.createLinkedAccount(user, TwoFactorProvider.KAKAO);
        coin = testData.createCoin(MARKET, "비트코인");
    }

    @Test
    @DisplayName("타임아웃 전에 주문이 체결되면 투자 완료로 기록하고 포트폴리오에 반영한다")
    void settlesFilledOrderBeforeTimeout() {
        PaymentEvent event = pendingTrade(BEFORE_TIMEOUT);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 200, ORDER_DONE_FILLED);

        tradePollingProcessor.processEvent(event.getId());

        assertInvested(event, "0.00007", "75000000");
        assertThat(cancelRequestCount()).isZero();
    }

    @Test
    @DisplayName("타임아웃 전에 주문이 아직 체결되지 않았으면 그대로 두고 다음 주기를 기다린다")
    void keepsWaitingWhileUnfilledBeforeTimeout() {
        PaymentEvent event = pendingTrade(BEFORE_TIMEOUT);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 200, ORDER_WAIT_UNFILLED);

        tradePollingProcessor.processEvent(event.getId());

        assertStillPendingTrade(event);
        assertThat(cancelRequestCount()).isZero();
    }

    @Test
    @DisplayName("타임아웃 전에 주문 조회가 실패하면 그대로 두고 다음 주기를 기다린다")
    void keepsWaitingWhenInquiryFailsBeforeTimeout() {
        PaymentEvent event = pendingTrade(BEFORE_TIMEOUT);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 500, SERVER_ERROR);

        tradePollingProcessor.processEvent(event.getId());

        assertStillPendingTrade(event);
        assertThat(cancelRequestCount()).isZero();
    }

    @Test
    @DisplayName("주문이 체결 없이 cancel로 끝나면 실패로 기록하고 원화가 업비트에 남았다고 알린다")
    void failsWhenOrderEndsWithoutFills() {
        PaymentEvent event = pendingTrade(BEFORE_TIMEOUT);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 200, ORDER_CANCEL_UNFILLED);

        tradePollingProcessor.processEvent(event.getId());

        PaymentEvent saved = reload(event);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getReason()).contains("체결 수량 0");
        verify(pushNotificationService).send(eq(user.getId()), eq(NotificationType.TRADE_FAILED), contains("업비트 계좌"), eq(event.getId()));
        assertThat(portfolioRepository.findByUserIdAndMarket(user.getId(), MARKET)).isEmpty();
    }

    @Test
    @DisplayName("타임아웃이 지난 뒤에 봤더라도 이미 체결된 주문은 투자 완료로 기록하고 취소를 요청하지 않는다")
    void settlesFilledOrderFoundAfterTimeout() {
        PaymentEvent event = pendingTrade(AFTER_TIMEOUT);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 200, ORDER_DONE_FILLED);
        UpbitMockServer.stubJson("DELETE", ORDER_PATH, 400, CANCEL_REJECTED);

        tradePollingProcessor.processEvent(event.getId());

        assertInvested(event, "0.00007", "75000000");
        assertThat(cancelRequestCount()).isZero();
    }

    @Test
    @DisplayName("타임아웃이 지나도록 일부만 체결된 주문은 취소한 뒤 체결분을 투자 완료로 기록한다")
    void settlesPartialFillAfterCancellingOnTimeout() {
        PaymentEvent event = pendingTrade(AFTER_TIMEOUT);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 200, ORDER_WAIT_PARTIALLY_FILLED);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 200, ORDER_CANCEL_PARTIALLY_FILLED);
        UpbitMockServer.stubJson("DELETE", ORDER_PATH, 200, CANCEL_ACCEPTED);

        tradePollingProcessor.processEvent(event.getId());

        assertInvested(event, "0.00004", "75000000");
        assertThat(UpbitMockServer.receivedRequests()).extracting(RecordedRequest::getMethod)
                .containsExactly("GET", "DELETE", "GET");
    }

    @Test
    @DisplayName("타임아웃이 지나도록 체결되지 않은 주문은 취소하고 타임아웃 실패로 기록한다")
    void failsWithTimeoutWhenUnfilledOrderIsCancelled() {
        PaymentEvent event = pendingTrade(AFTER_TIMEOUT);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 200, ORDER_WAIT_UNFILLED);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 200, ORDER_CANCEL_UNFILLED);
        UpbitMockServer.stubJson("DELETE", ORDER_PATH, 200, CANCEL_ACCEPTED);

        tradePollingProcessor.processEvent(event.getId());

        PaymentEvent saved = reload(event);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getReason()).contains("10분 타임아웃");
        verify(pushNotificationService).send(eq(user.getId()), eq(NotificationType.TRADE_TIMEOUT), anyString(), eq(event.getId()));
        assertThat(cancelRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("타임아웃 뒤 주문 취소가 실패하면 실패로 확정하지 않고 다음 주기를 기다린다")
    void keepsWaitingWhenCancelFailsAfterTimeout() {
        PaymentEvent event = pendingTrade(AFTER_TIMEOUT);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 200, ORDER_WAIT_UNFILLED);
        UpbitMockServer.stubJson("DELETE", ORDER_PATH, 500, SERVER_ERROR);

        tradePollingProcessor.processEvent(event.getId());

        assertStillPendingTrade(event);
    }

    @Test
    @DisplayName("타임아웃 뒤 취소를 요청했지만 아직 반영되지 않았으면 다음 주기를 기다린다")
    void keepsWaitingWhenCancelIsNotReflectedYet() {
        PaymentEvent event = pendingTrade(AFTER_TIMEOUT);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 200, ORDER_WAIT_UNFILLED);
        UpbitMockServer.stubJson("DELETE", ORDER_PATH, 200, CANCEL_ACCEPTED);

        tradePollingProcessor.processEvent(event.getId());

        assertStillPendingTrade(event);
    }

    @Test
    @DisplayName("타임아웃 뒤 주문 조회가 실패하면 취소를 요청하지 않고 다음 주기를 기다린다")
    void keepsWaitingWithoutCancellingWhenInquiryFailsAfterTimeout() {
        PaymentEvent event = pendingTrade(AFTER_TIMEOUT);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 500, SERVER_ERROR);
        UpbitMockServer.stubJson("DELETE", ORDER_PATH, 200, CANCEL_ACCEPTED);

        tradePollingProcessor.processEvent(event.getId());

        assertStillPendingTrade(event);
        assertThat(cancelRequestCount()).isZero();
    }

    @Test
    @DisplayName("재시도 기한이 지나도록 주문 조회가 실패하면 확인 불가로 종결하고 업비트에서 확인하라고 알린다")
    void closesAsUnconfirmedWhenInquiryKeepsFailing() {
        PaymentEvent event = pendingTrade(AFTER_RETRY_LIMIT);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 500, SERVER_ERROR);

        tradePollingProcessor.processEvent(event.getId());

        assertClosedAsUnconfirmed(event);
        assertThat(cancelRequestCount()).isZero();
    }

    @Test
    @DisplayName("재시도 기한이 지나도록 주문 취소가 실패하면 확인 불가로 종결한다")
    void closesAsUnconfirmedWhenCancelKeepsFailing() {
        PaymentEvent event = pendingTrade(AFTER_RETRY_LIMIT);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 200, ORDER_WAIT_UNFILLED);
        UpbitMockServer.stubJson("DELETE", ORDER_PATH, 500, SERVER_ERROR);

        tradePollingProcessor.processEvent(event.getId());

        assertClosedAsUnconfirmed(event);
    }

    @Test
    @DisplayName("재시도 기한이 지나도록 취소가 반영되지 않으면 확인 불가로 종결한다")
    void closesAsUnconfirmedWhenCancelIsNeverReflected() {
        PaymentEvent event = pendingTrade(AFTER_RETRY_LIMIT);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 200, ORDER_WAIT_UNFILLED);
        UpbitMockServer.stubJson("DELETE", ORDER_PATH, 200, CANCEL_ACCEPTED);

        tradePollingProcessor.processEvent(event.getId());

        assertClosedAsUnconfirmed(event);
    }

    @ParameterizedTest(name = "주문 접수 뒤 {0}분")
    @DisplayName("업비트가 401을 주면 실패로 기록하고 키를 다시 연동하라고 알린다")
    @ValueSource(ints = {3, 11})
    void failsWithInvalidKey(int elapsedMinutes) {
        PaymentEvent event = pendingTrade(Duration.ofMinutes(elapsedMinutes));
        UpbitMockServer.stubJson("GET", ORDER_PATH, 401, INVALID_KEY);
        UpbitMockServer.stubJson("DELETE", ORDER_PATH, 401, INVALID_KEY);

        tradePollingProcessor.processEvent(event.getId());

        PaymentEvent saved = reload(event);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getReason()).contains("401");
        verify(pushNotificationService).send(eq(user.getId()), eq(NotificationType.UPBIT_INVALID_KEY), anyString(), eq(event.getId()));
    }

    private PaymentEvent pendingTrade(Duration elapsed) {
        return testData.createPendingTradeEvent(user.getId(), coin, TRADE_UUID, elapsed);
    }

    private PaymentEvent reload(PaymentEvent event) {
        return paymentEventRepository.findById(event.getId()).orElseThrow();
    }

    private long cancelRequestCount() {
        List<RecordedRequest> requests = UpbitMockServer.receivedRequests();
        return requests.stream().filter(request -> "DELETE".equals(request.getMethod())).count();
    }

    private void assertInvested(PaymentEvent event, String volume, String price) {
        PaymentEvent saved = reload(event);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.INVESTED);
        assertThat(saved.getInvestedVolume()).isEqualByComparingTo(volume);
        assertThat(saved.getInvestedPrice()).isEqualByComparingTo(price);
        assertThat(saved.getReason()).isNull();

        Portfolio portfolio = portfolioRepository.findByUserIdAndMarket(user.getId(), MARKET).orElseThrow();
        assertThat(portfolio.getQuantity()).isEqualByComparingTo(volume);
        assertThat(portfolio.getAveragePrice()).isEqualByComparingTo(price);

        verify(pushNotificationService).send(eq(user.getId()), eq(NotificationType.TRADE_SUCCESS), anyString(), eq(event.getId()));
    }

    private void assertStillPendingTrade(PaymentEvent event) {
        PaymentEvent saved = reload(event);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING_TRADE);
        assertThat(saved.getReason()).isNull();
        assertThat(portfolioRepository.findByUserIdAndMarket(user.getId(), MARKET)).isEmpty();
        verifyNoInteractions(pushNotificationService);
    }

    private void assertClosedAsUnconfirmed(PaymentEvent event) {
        PaymentEvent saved = reload(event);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getReason()).contains("확인하지 못해");
        assertThat(portfolioRepository.findByUserIdAndMarket(user.getId(), MARKET)).isEmpty();
        verify(pushNotificationService).send(eq(user.getId()), eq(NotificationType.TRADE_FAILED), contains("확인하지 못했어요"), eq(event.getId()));
    }
}
