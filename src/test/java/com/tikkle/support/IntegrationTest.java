package com.tikkle.support;

import com.tikkle.insight.scheduler.MarketTopicScheduler;
import com.tikkle.investment.scheduler.AiPortfolioScheduler;
import com.tikkle.investment.scheduler.CoinSyncScheduler;
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
 * </ul>
 *
 * 가짜로 바꾸는 빈을 이 클래스에 모아 두면 모든 테스트가 스프링 컨텍스트 하나를 함께 씁니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, DatabaseCleaner.class})
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

    @Autowired
    protected MockMvc mockMvc;

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
