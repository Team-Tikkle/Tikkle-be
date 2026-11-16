package com.tikkle;

import com.tikkle.global.security.jwt.JwtProvider;
import com.tikkle.support.IntegrationTest;
import com.tikkle.support.UpbitMockServer;
import com.tikkle.upbit.client.UpbitDepositClient;
import com.tikkle.upbit.dto.response.UpbitDepositResponse;
import com.tikkle.upbit.exception.UpbitInvalidKeyException;
import com.tikkle.user.entity.User;
import com.tikkle.user.repository.UserRepository;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 테스트 환경 자체가 의도대로 구성됐는지 확인합니다.
 */
class TestEnvironmentTest extends IntegrationTest {

    private static final String ACCESS_KEY = "test-access-key";
    private static final String SECRET_KEY = "test-secret-key-0123456789-0123456789";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UpbitDepositClient upbitDepositClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("테스트 DB는 운영과 같은 MySQL 8.4이다")
    void usesMysql84() {
        String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);

        assertThat(version).startsWith("8.4");
    }

    @Test
    @DisplayName("Redis에 값을 쓰고 읽을 수 있다")
    void readsAndWritesRedis() {
        redisTemplate.opsForValue().set("test:key", "value");

        assertThat(redisTemplate.opsForValue().get("test:key")).isEqualTo("value");
    }

    @Test
    @DisplayName("스케줄러는 가짜로 대체되고, 서버가 뜨는 동안 업비트를 호출하지 않는다")
    void doesNotCallUpbitOnStartup() {
        assertThat(mockingDetails(coinSyncScheduler).isMock()).isTrue();
        assertThat(mockingDetails(aiPortfolioScheduler).isMock()).isTrue();
        assertThat(mockingDetails(marketTopicScheduler).isMock()).isTrue();
        assertThat(mockingDetails(upbitDepositPollingScheduler).isMock()).isTrue();
        assertThat(mockingDetails(upbitTradePollingScheduler).isMock()).isTrue();
        assertThat(mockingDetails(pendingOrderExpirationScheduler).isMock()).isTrue();

        assertThat(UpbitMockServer.requestCountBeforeFirstTest()).isZero();
    }

    @Test
    @DisplayName("업비트 클라이언트의 요청은 대역 서버로 가고, 준비한 응답을 받는다")
    void upbitClientTalksToMockServer() {
        UpbitMockServer.enqueueJson(200, """
                {"uuid": "deposit-uuid-1", "state": "ACCEPTED"}
                """);

        UpbitDepositResponse response = upbitDepositClient.getDepositDetails("deposit-uuid-1", ACCESS_KEY, SECRET_KEY);

        assertThat(response.uuid()).isEqualTo("deposit-uuid-1");
        assertThat(response.state()).isEqualTo("ACCEPTED");

        List<RecordedRequest> requests = UpbitMockServer.receivedRequests();
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getPath()).contains("deposit-uuid-1");
        assertThat(requests.get(0).getHeader("Authorization")).startsWith("Bearer ");
    }

    @Test
    @DisplayName("업비트가 401을 주면 키 오류 예외가 난다")
    void mapsUnauthorizedToInvalidKey() {
        UpbitMockServer.enqueueJson(401, """
                {"error": {"name": "invalid_access_key", "message": "잘못된 엑세스 키입니다."}}
                """);

        assertThatThrownBy(() -> upbitDepositClient.getDepositDetails("deposit-uuid-1", ACCESS_KEY, SECRET_KEY))
                .isInstanceOf(UpbitInvalidKeyException.class);
    }

    @Test
    @DisplayName("토큰 없이 보호된 API를 부르면 401과 AUTH-001을 받는다")
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    @Test
    @DisplayName("발급한 토큰으로 내 정보를 조회할 수 있다")
    void returnsMyInfoWithToken() throws Exception {
        User user = userRepository.save(User.builder()
                .name("테스트")
                .phoneNumber("01012345678")
                .password("encoded-password")
                .build());
        String accessToken = jwtProvider.createAccessToken(user.getId());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.phoneNumber").value("01012345678"));
    }
}
