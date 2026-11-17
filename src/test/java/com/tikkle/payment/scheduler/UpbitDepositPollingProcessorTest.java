package com.tikkle.payment.scheduler;

import com.tikkle.investment.entity.Coin;
import com.tikkle.investment.repository.PortfolioRepository;
import com.tikkle.notification.entity.enums.NotificationType;
import com.tikkle.payment.entity.PaymentEvent;
import com.tikkle.payment.entity.enums.PaymentStatus;
import com.tikkle.payment.repository.PaymentEventRepository;
import com.tikkle.payment.sse.SseConnectionManager;
import com.tikkle.support.IntegrationTest;
import com.tikkle.support.UpbitMockServer;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.TwoFactorProvider;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
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
 * 입금 폴러가 입금 대기 한 건을 어떻게 판정하는지 업비트 대역 서버로 확인합니다.
 * 입금 요청 뒤 지난 시간은 요청 시각을 과거로 돌려서 만듭니다.
 */
class UpbitDepositPollingProcessorTest extends IntegrationTest {

    private static final String MARKET = "KRW-BTC";
    private static final String DEPOSIT_UUID = "deposit-uuid-1";
    private static final String DEPOSIT_PATH = "/v1/deposit?uuid=" + DEPOSIT_UUID;
    private static final String ORDER_UUID = "order-uuid-1";
    private static final String ORDER_PATH = "/v1/order?uuid=" + ORDER_UUID;

    // 2차 인증 타임아웃은 입금 요청 뒤 210초, 확인 재시도 기한은 그 뒤 10분이다
    private static final Duration BEFORE_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration AFTER_TIMEOUT = Duration.ofSeconds(300);
    private static final Duration AFTER_RETRY_LIMIT = Duration.ofSeconds(900);

    private static final String DEPOSIT_WAITING = """
            {"type": "deposit", "uuid": "deposit-uuid-1", "currency": "KRW", "state": "PROCESSING", "amount": "5500"}
            """;
    private static final String DEPOSIT_ACCEPTED = """
            {"type": "deposit", "uuid": "deposit-uuid-1", "currency": "KRW", "state": "ACCEPTED", "amount": "5500"}
            """;
    private static final String ORDER_ACCEPTED = """
            {"uuid": "order-uuid-1", "side": "bid", "ord_type": "price", "state": "wait", "market": "KRW-BTC"}
            """;
    private static final String ORDER_DONE_FILLED = """
            {"uuid": "order-uuid-1", "state": "done", "market": "KRW-BTC", "trades": [
              {"market": "KRW-BTC", "uuid": "trade-1", "price": "75000000", "volume": "0.00007", "funds": "5250", "side": "bid"}
            ]}
            """;
    private static final String ORDER_NOT_FOUND = """
            {"error": {"name": "order_not_found", "message": "주문을 찾지 못했습니다."}}
            """;
    private static final String SERVER_ERROR = """
            {"error": {"name": "server_error", "message": "일시적인 오류입니다."}}
            """;
    private static final String TOO_MANY_REQUESTS = """
            {"error": {"name": "too_many_requests", "message": "요청 수 제한을 초과했습니다."}}
            """;
    private static final String INVALID_KEY = """
            {"error": {"name": "invalid_access_key", "message": "잘못된 엑세스 키입니다."}}
            """;

    @Autowired
    private UpbitDepositPollingProcessor depositPollingProcessor;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private SseConnectionManager sseConnectionManager;

    private User user;
    private Coin coin;
    private Long eventIdWithConnection;

    @BeforeEach
    void setUp() {
        user = testData.createUser();
        testData.createLinkedAccount(user, TwoFactorProvider.KAKAO);
        coin = testData.createCoin(MARKET, "비트코인");
    }

    @AfterEach
    void removeLeftoverConnection() {
        // 연결 관리자는 모든 테스트가 함께 쓰므로, 테스트가 만든 연결을 남기지 않는다
        if (eventIdWithConnection != null) {
            sseConnectionManager.complete(eventIdWithConnection);
        }
    }

    @ParameterizedTest(name = "입금 요청 뒤 {0}초")
    @DisplayName("입금이 도착하면 2차 인증 타임아웃이 지났더라도 매수 주문을 내고, 체결되면 투자 완료로 기록한다")
    @ValueSource(ints = {60, 300})
    void buysWhenDepositArrives(int elapsedSeconds) {
        PaymentEvent event = pendingDeposit(Duration.ofSeconds(elapsedSeconds));
        UpbitMockServer.stubJson("GET", DEPOSIT_PATH, 200, DEPOSIT_ACCEPTED);
        stubOrderFilled();

        depositPollingProcessor.processEvent(event.getId());

        assertInvested(event);
        List<RecordedRequest> orderRequests = requestsTo("POST", "/v1/orders");
        assertThat(orderRequests).hasSize(1);
        assertThat(orderRequests.get(0).getBody().readUtf8()).contains("tikkle-" + event.getId()).contains(MARKET);
    }

    @ParameterizedTest(name = "입금 상태 {0}")
    @DisplayName("입금이 거절되거나 취소되면 실패로 기록하고 출금된 금액이 없다고 알린다")
    @ValueSource(strings = {"REJECTED", "CANCELED"})
    void failsWhenDepositIsRejected(String depositState) {
        PaymentEvent event = pendingDeposit(BEFORE_TIMEOUT);
        UpbitMockServer.stubJson("GET", DEPOSIT_PATH, 200, """
                {"type": "deposit", "uuid": "deposit-uuid-1", "currency": "KRW", "state": "%s", "amount": "5500"}
                """.formatted(depositState));

        depositPollingProcessor.processEvent(event.getId());

        PaymentEvent saved = reload(event);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getReason()).contains("입금 거절 또는 취소");
        verify(pushNotificationService).send(eq(user.getId()), eq(NotificationType.DEPOSIT_FAILED), contains("출금된 금액은 없습니다"), eq(event.getId()));
        assertThat(requestsTo("POST", "/v1/orders")).isEmpty();
    }

    @Test
    @DisplayName("2차 인증을 기다리는 중이고 타임아웃 전이면 그대로 두고 다음 주기를 기다린다")
    void keepsWaitingForTwoFactorAuth() {
        PaymentEvent event = pendingDeposit(BEFORE_TIMEOUT);
        UpbitMockServer.stubJson("GET", DEPOSIT_PATH, 200, DEPOSIT_WAITING);

        depositPollingProcessor.processEvent(event.getId());

        assertStillPendingDeposit(event);
    }

    @Test
    @DisplayName("입금이 도착하지 않은 채 타임아웃이 지나면 승인 대기로 되돌리고 다시 승인하라고 알린다")
    void revertsWhenTwoFactorAuthTimesOut() {
        PaymentEvent event = pendingDeposit(AFTER_TIMEOUT);
        UpbitMockServer.stubJson("GET", DEPOSIT_PATH, 200, DEPOSIT_WAITING);

        depositPollingProcessor.processEvent(event.getId());

        PaymentEvent saved = reload(event);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING_PURCHASE);
        assertThat(saved.getDepositUuid()).isNull();
        verify(pushNotificationService).send(eq(user.getId()), eq(NotificationType.DEPOSIT_TIMEOUT), anyString(), eq(event.getId()));
    }

    @Test
    @DisplayName("입금은 도착했지만 매수 주문이 접수되지 않으면 실패로 기록하고 원화가 업비트에 남았다고 알린다")
    void failsWhenOrderIsNotAccepted() {
        PaymentEvent event = pendingDeposit(BEFORE_TIMEOUT);
        UpbitMockServer.stubJson("GET", DEPOSIT_PATH, 200, DEPOSIT_ACCEPTED);
        UpbitMockServer.stubJson("POST", "/v1/orders", 500, SERVER_ERROR);
        UpbitMockServer.stubJson("GET", "/v1/order?identifier=tikkle-" + event.getId(), 404, ORDER_NOT_FOUND);

        depositPollingProcessor.processEvent(event.getId());

        PaymentEvent saved = reload(event);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getReason()).contains("매수 주문 접수 실패");
        verify(pushNotificationService).send(eq(user.getId()), eq(NotificationType.TRADE_FAILED), contains("업비트 계좌"), eq(event.getId()));
    }

    @Test
    @DisplayName("업비트가 401을 주면 실패로 기록하고 키를 다시 연동하라고 알린다")
    void failsWithInvalidKey() {
        PaymentEvent event = pendingDeposit(BEFORE_TIMEOUT);
        UpbitMockServer.stubJson("GET", DEPOSIT_PATH, 401, INVALID_KEY);

        depositPollingProcessor.processEvent(event.getId());

        PaymentEvent saved = reload(event);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getReason()).contains("401");
        verify(pushNotificationService).send(eq(user.getId()), eq(NotificationType.UPBIT_INVALID_KEY), anyString(), eq(event.getId()));
    }

    @ParameterizedTest(name = "업비트 응답 {0}")
    @DisplayName("입금 상태 조회가 일시적으로 실패하면 실패로 확정하지 않고 다음 주기를 기다린다")
    @ValueSource(ints = {500, 503, 429})
    void keepsWaitingWhenInquiryFails(int status) {
        PaymentEvent event = pendingDeposit(BEFORE_TIMEOUT);
        UpbitMockServer.stubJson("GET", DEPOSIT_PATH, status, status == 429 ? TOO_MANY_REQUESTS : SERVER_ERROR);

        depositPollingProcessor.processEvent(event.getId());

        assertStillPendingDeposit(event);
    }

    @Test
    @DisplayName("타임아웃이 지난 뒤에도 입금 상태 조회가 실패하면 승인 대기로 되돌리지 않고 다음 주기를 기다린다")
    void keepsWaitingWhenInquiryFailsAfterTimeout() {
        PaymentEvent event = pendingDeposit(AFTER_TIMEOUT);
        UpbitMockServer.stubJson("GET", DEPOSIT_PATH, 500, SERVER_ERROR);

        depositPollingProcessor.processEvent(event.getId());

        assertStillPendingDeposit(event);
    }

    @Test
    @DisplayName("입금 상태 조회가 실패했다가 회복되면 회복된 조회 결과로 매수를 진행한다")
    void buysAfterInquiryRecovers() {
        PaymentEvent event = pendingDeposit(BEFORE_TIMEOUT);
        UpbitMockServer.stubJson("GET", DEPOSIT_PATH, 500, SERVER_ERROR);
        UpbitMockServer.stubJson("GET", DEPOSIT_PATH, 200, DEPOSIT_ACCEPTED);
        stubOrderFilled();

        depositPollingProcessor.processEvent(event.getId());
        assertStillPendingDeposit(event);

        depositPollingProcessor.processEvent(event.getId());
        assertInvested(event);
    }

    @Test
    @DisplayName("재시도 기한이 지나도록 입금 상태 조회가 실패하면 확인 불가로 종결하고 업비트에서 확인하라고 알린다")
    void closesAsUnconfirmedWhenInquiryKeepsFailing() {
        PaymentEvent event = pendingDeposit(AFTER_RETRY_LIMIT);
        UpbitMockServer.stubJson("GET", DEPOSIT_PATH, 500, SERVER_ERROR);

        depositPollingProcessor.processEvent(event.getId());

        PaymentEvent saved = reload(event);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getReason()).contains("확인하지 못해");
        verify(pushNotificationService).send(eq(user.getId()), eq(NotificationType.DEPOSIT_FAILED), contains("확인하지 못해"), eq(event.getId()));
        assertThat(requestsTo("POST", "/v1/orders")).isEmpty();
    }

    @Test
    @DisplayName("닫힌 SSE 연결이 남아 있어도 2차 인증을 기다리는 건을 실패로 만들지 않는다")
    void keepsWaitingWhenSseConnectionIsAlreadyClosed() {
        PaymentEvent event = pendingDeposit(BEFORE_TIMEOUT);
        leaveClosedConnection(event);
        UpbitMockServer.stubJson("GET", DEPOSIT_PATH, 200, DEPOSIT_WAITING);

        depositPollingProcessor.processEvent(event.getId());

        assertStillPendingDeposit(event);
    }

    @Test
    @DisplayName("닫힌 SSE 연결이 남아 있어도 체결된 매수를 투자 완료로 기록하고 푸시로 알린다")
    void recordsInvestmentWhenSseConnectionIsAlreadyClosed() {
        PaymentEvent event = pendingDeposit(BEFORE_TIMEOUT);
        leaveClosedConnection(event);
        UpbitMockServer.stubJson("GET", DEPOSIT_PATH, 200, DEPOSIT_ACCEPTED);
        stubOrderFilled();

        depositPollingProcessor.processEvent(event.getId());

        assertInvested(event);
    }

    @Test
    @DisplayName("예기치 못한 오류의 메시지가 길어도 실패 기록이 저장된다")
    void storesFailureEvenWhenErrorMessageIsLong() {
        PaymentEvent event = pendingDeposit(BEFORE_TIMEOUT);

        depositPollingProcessor.handleGeneralError(event.getId(), "오류".repeat(150));

        PaymentEvent saved = reload(event);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getReason()).startsWith("매수 처리 중 예기치 못한 오류").hasSize(255);
    }

    private PaymentEvent pendingDeposit(Duration elapsed) {
        return testData.createPendingDepositEvent(user.getId(), coin, DEPOSIT_UUID, elapsed);
    }

    private void stubOrderFilled() {
        UpbitMockServer.stubJson("POST", "/v1/orders", 201, ORDER_ACCEPTED);
        UpbitMockServer.stubJson("GET", ORDER_PATH, 200, ORDER_DONE_FILLED);
    }

    /**
     * 이미 닫혔지만 연결 관리자에 아직 남아 있는 SSE 연결을 만듭니다.
     * 연결이 닫히면 관리자에서 지우는 콜백이 비동기로 돌기 때문에, 닫힌 직후에는 이런 상태가 잠시 생깁니다.
     */
    private void leaveClosedConnection(PaymentEvent event) {
        eventIdWithConnection = event.getId();
        sseConnectionManager.createEmitter(event.getId()).complete();
    }

    private PaymentEvent reload(PaymentEvent event) {
        return paymentEventRepository.findById(event.getId()).orElseThrow();
    }

    private List<RecordedRequest> requestsTo(String method, String path) {
        return UpbitMockServer.receivedRequests().stream()
                .filter(request -> method.equals(request.getMethod()) && path.equals(request.getPath()))
                .toList();
    }

    private void assertInvested(PaymentEvent event) {
        PaymentEvent saved = reload(event);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.INVESTED);
        assertThat(saved.getInvestedVolume()).isEqualByComparingTo("0.00007");
        assertThat(saved.getInvestedPrice()).isEqualByComparingTo("75000000");
        assertThat(portfolioRepository.findByUserIdAndMarket(user.getId(), MARKET)).isPresent();
        verify(pushNotificationService).send(eq(user.getId()), eq(NotificationType.TRADE_SUCCESS), anyString(), eq(event.getId()));
    }

    private void assertStillPendingDeposit(PaymentEvent event) {
        PaymentEvent saved = reload(event);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING_DEPOSIT);
        assertThat(saved.getDepositUuid()).isEqualTo(DEPOSIT_UUID);
        assertThat(saved.getReason()).isNull();
        verifyNoInteractions(pushNotificationService);
        assertThat(requestsTo("POST", "/v1/orders")).isEmpty();
    }
}
