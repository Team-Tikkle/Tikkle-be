package com.tikkle.global.config;

import com.tikkle.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 헬스 엔드포인트가 서비스 포트와 다른 관리 포트에만 열리는지 확인합니다.
 * 운영에서는 관리 포트(8081)를 서버 안(127.0.0.1)에서만 열어, 배포 스크립트와 컨테이너 헬스체크만 본다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0"
)
class ManagementHealthTest extends IntegrationTest {

    @Autowired
    private Environment environment;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    @DisplayName("헬스 엔드포인트는 관리 포트에서 인증 없이 UP을 돌려준다")
    void healthIsUpOnManagementPort() throws Exception {
        HttpResponse<String> response = get(managementPort(), "/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("헬스 엔드포인트는 서비스 포트에서는 열리지 않는다")
    void healthIsNotServedOnServerPort() throws Exception {
        HttpResponse<String> response = get(serverPort(), "/actuator/health");

        assertThat(response.statusCode()).isNotEqualTo(200);
        assertThat(response.body()).doesNotContain("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("헬스 응답에는 구성 요소의 상세(DB·Redis 정보)를 넣지 않는다")
    void healthHidesDetails() throws Exception {
        HttpResponse<String> response = get(managementPort(), "/actuator/health");

        assertThat(response.body()).doesNotContain("components").doesNotContain("details");
    }

    // 관리 포트는 actuator가 있어야 생긴다. 없으면 이 값이 없어 테스트가 실패한다.
    private int managementPort() {
        return Integer.parseInt(environment.getRequiredProperty("local.management.port"));
    }

    private int serverPort() {
        return Integer.parseInt(environment.getRequiredProperty("local.server.port"));
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
