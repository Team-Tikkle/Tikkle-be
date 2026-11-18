package com.tikkle.support;

import com.tikkle.insight.scheduler.MarketTopicScheduler;
import com.tikkle.investment.scheduler.AiPortfolioScheduler;
import com.tikkle.investment.scheduler.CoinSyncScheduler;
import com.tikkle.investment.service.TargetCoinRecommendationService;
import com.tikkle.notification.service.PushNotificationService;
import com.tikkle.payment.scheduler.PendingOrderExpirationScheduler;
import com.tikkle.payment.scheduler.UpbitDepositPollingScheduler;
import com.tikkle.payment.scheduler.UpbitTradePollingScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 서버 전체를 띄워 검증하는 테스트의 공통 부모 클래스입니다.
 *
 * <ul>
 *   <li>MySQL·Redis 는 Testcontainers 로 띄웁니다.</li>
 *   <li>업비트 API 는 {@link UpbitMockServer} 가 대신합니다.</li>
 *   <li>스케줄러는 가짜로 바꿉니다. 기동 시 실행되는 작업과 주기 폴링이 테스트 도중 돌지 않게 하기 위해서입니다.
 *       폴링이 하는 일은 테스트가 프로세서를 직접 호출해 검증합니다.</li>
 *   <li>AI 모델도 가짜로 바꿔 실제 호출이 나가지 않게 합니다.</li>
 *   <li>푸시 알림 발송도 가짜로 바꿉니다. 테스트 환경에는 FCM 이 꺼져 있어 원래도 발송되지 않으며,
 *       가짜로 바꾸면 어떤 종류의 알림을 보내려 했는지 검증할 수 있습니다.</li>
 *   <li>코인 추천은 실제 빈을 그대로 쓰되 감싸 둡니다. 동작은 바뀌지 않고, 몇 번 호출됐는지만 셀 수 있습니다.</li>
 * </ul>
 *
 * 가짜로 바꾸는 빈을 이 클래스에 모아 두면 모든 테스트가 스프링 컨텍스트 하나를 함께 씁니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, DatabaseCleaner.class, TestDataFactory.class, PaymentIngestRequests.class})
public abstract class IntegrationTest {

    @MockitoBean
    protected UpbitDepositPollingScheduler upbitDepositPollingScheduler;

    @MockitoBean
    protected UpbitTradePollingScheduler upbitTradePollingScheduler;

    @MockitoBean
    protected PendingOrderExpirationScheduler pendingOrderExpirationScheduler;

    @MockitoBean
    protected CoinSyncScheduler coinSyncScheduler;

    @MockitoBean
    protected AiPortfolioScheduler aiPortfolioScheduler;

    @MockitoBean
    protected MarketTopicScheduler marketTopicScheduler;

    @MockitoBean(name = "googleGenAiChatModel")
    protected ChatModel googleGenAiChatModel;

    @MockitoBean(name = "openAiChatModel")
    protected ChatModel openAiChatModel;

    @MockitoBean
    protected PushNotificationService pushNotificationService;

    @MockitoSpyBean
    protected TargetCoinRecommendationService targetCoinRecommendationService;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected TestDataFactory testData;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @DynamicPropertySource
    static void upbitProperties(DynamicPropertyRegistry registry) {
        registry.add("upbit.api.base-url", UpbitMockServer::baseUrl);
    }

    @BeforeEach
    void resetTestState() {
        databaseCleaner.clean();
        UpbitMockServer.reset();
    }
}
