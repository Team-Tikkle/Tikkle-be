package com.tikkle.upbit.service;

import com.tikkle.support.IntegrationTest;
import com.tikkle.support.UpbitMockServer;
import com.tikkle.upbit.exception.UpbitInvalidKeyException;
import com.tikkle.upbit.exception.UpbitOrderFailedException;
import com.tikkle.upbit.service.UpbitTradeService.TradeResult;
import com.tikkle.user.entity.User;
import com.tikkle.user.entity.enums.TwoFactorProvider;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 시장가 매수 주문과 체결 합산을 업비트 대역 서버로 확인합니다.
 */
class UpbitTradeServiceTest extends IntegrationTest {

    private static final String MARKET = "KRW-BTC";
    private static final String IDENTIFIER = "tikkle-1";
    private static final String ORDER_ACCEPTED = """
            {"uuid": "order-uuid-1", "side": "bid", "ord_type": "price", "state": "wait", "market": "KRW-BTC"}
            """;
    private static final String SERVER_ERROR = """
            {"error": {"name": "server_error", "message": "일시적인 오류입니다."}}
            """;

    @Autowired
    private UpbitTradeService upbitTradeService;

    private User user;

    @BeforeEach
    void setUp() {
        user = testData.createUser();
        testData.createLinkedAccount(user, TwoFactorProvider.KAKAO);
    }

    @Test
    @DisplayName("주문이 done이면 체결 내역을 합산해 수량과 가중 평균 단가를 돌려준다")
    void sumsTradesWhenOrderIsDone() {
        UpbitMockServer.enqueueJson(201, ORDER_ACCEPTED);
        UpbitMockServer.enqueueJson(200, """
                {"uuid": "order-uuid-1", "state": "done", "market": "KRW-BTC", "trades": [
                  {"market": "KRW-BTC", "uuid": "trade-1", "price": "200", "volume": "0.5", "funds": "100", "side": "bid"},
                  {"market": "KRW-BTC", "uuid": "trade-2", "price": "202", "volume": "0.25", "funds": "50.5", "side": "bid"}
                ]}
                """);

        TradeResult result = upbitTradeService.executeTrade(user.getId(), MARKET, 5500, IDENTIFIER);

        assertThat(result.isPending()).isFalse();
        assertThat(result.tradeUuid()).isEqualTo("order-uuid-1");
        assertThat(result.executedVolume()).isEqualByComparingTo("0.75");
        // 150.5 / 0.75 = 200.6666… → 소수 넷째 자리에서 반올림
        assertThat(result.executedPrice()).isEqualByComparingTo("200.6667");

        List<RecordedRequest> requests = UpbitMockServer.receivedRequests();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getMethod()).isEqualTo("POST");
        assertThat(requests.get(0).getPath()).isEqualTo("/v1/orders");
        assertThat(requests.get(0).getBody().readUtf8()).contains(IDENTIFIER).contains(MARKET);
        assertThat(requests.get(1).getPath()).isEqualTo("/v1/order?uuid=order-uuid-1");
    }

    @Test
    @DisplayName("일부만 체결되고 잔량이 cancel된 주문도 체결분을 합산한다")
    void sumsPartialFillWhenRemainderIsCancelled() {
        UpbitMockServer.enqueueJson(201, ORDER_ACCEPTED);
        UpbitMockServer.enqueueJson(200, """
                {"uuid": "order-uuid-1", "state": "cancel", "market": "KRW-BTC", "trades": [
                  {"market": "KRW-BTC", "uuid": "trade-1", "price": "75000000", "volume": "0.00004", "funds": "3000", "side": "bid"}
                ]}
                """);

        TradeResult result = upbitTradeService.executeTrade(user.getId(), MARKET, 5500, IDENTIFIER);

        assertThat(result.isPending()).isFalse();
        assertThat(result.executedVolume()).isEqualByComparingTo("0.00004");
        assertThat(result.executedPrice()).isEqualByComparingTo("75000000");
    }

    @Test
    @DisplayName("주문 접수 뒤 체결 확인에 실패해도 예외 없이 주문 uuid를 돌려준다")
    void keepsOrderUuidWhenExecutionCheckFails() {
        UpbitMockServer.enqueueJson(201, ORDER_ACCEPTED);
        UpbitMockServer.enqueueJson(500, SERVER_ERROR);

        TradeResult result = upbitTradeService.executeTrade(user.getId(), MARKET, 5500, IDENTIFIER);

        assertThat(result.isPending()).isTrue();
        assertThat(result.tradeUuid()).isEqualTo("order-uuid-1");
        assertThat(result.executedVolume()).isNull();
        assertThat(result.executedPrice()).isNull();
    }

    @Test
    @DisplayName("주문 요청이 실패해도 식별자로 접수된 주문이 확인되면 새로 주문하지 않고 그 주문을 이어서 추적한다")
    void continuesWithAcceptedOrderWhenPlaceOrderFails() {
        UpbitMockServer.enqueueJson(500, SERVER_ERROR);
        UpbitMockServer.enqueueJson(200, """
                {"uuid": "order-uuid-9", "state": "wait", "market": "KRW-BTC"}
                """);
        UpbitMockServer.enqueueJson(200, """
                {"uuid": "order-uuid-9", "state": "done", "market": "KRW-BTC", "trades": [
                  {"market": "KRW-BTC", "uuid": "trade-1", "price": "75000000", "volume": "0.00007", "funds": "5250", "side": "bid"}
                ]}
                """);

        TradeResult result = upbitTradeService.executeTrade(user.getId(), MARKET, 5500, IDENTIFIER);

        assertThat(result.isPending()).isFalse();
        assertThat(result.tradeUuid()).isEqualTo("order-uuid-9");

        List<RecordedRequest> requests = UpbitMockServer.receivedRequests();
        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).getPath()).isEqualTo("/v1/orders");
        assertThat(requests.get(1).getPath()).isEqualTo("/v1/order?identifier=" + IDENTIFIER);
        assertThat(requests.get(2).getPath()).isEqualTo("/v1/order?uuid=order-uuid-9");
        assertThat(requests).filteredOn(request -> "POST".equals(request.getMethod())).hasSize(1);
    }

    @Test
    @DisplayName("주문 요청이 실패했고 식별자로도 주문이 없으면 주문 실패 예외가 난다")
    void failsWhenOrderWasNotAccepted() {
        UpbitMockServer.enqueueJson(500, SERVER_ERROR);
        UpbitMockServer.enqueueJson(404, """
                {"error": {"name": "order_not_found", "message": "주문을 찾지 못했습니다."}}
                """);

        assertThatThrownBy(() -> upbitTradeService.executeTrade(user.getId(), MARKET, 5500, IDENTIFIER))
                .isInstanceOf(UpbitOrderFailedException.class);

        assertThat(UpbitMockServer.receivedRequests()).hasSize(2);
    }

    @Test
    @DisplayName("업비트가 401을 주면 식별자 조회 없이 바로 키 오류 예외가 난다")
    void propagatesInvalidKeyWithoutLookup() {
        UpbitMockServer.enqueueJson(401, """
                {"error": {"name": "invalid_access_key", "message": "잘못된 엑세스 키입니다."}}
                """);

        assertThatThrownBy(() -> upbitTradeService.executeTrade(user.getId(), MARKET, 5500, IDENTIFIER))
                .isInstanceOf(UpbitInvalidKeyException.class);

        assertThat(UpbitMockServer.receivedRequests()).hasSize(1);
    }
}
