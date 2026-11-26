package com.tikkle.global.config;

import com.tikkle.support.IntegrationTest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 서블릿 컨테이너를 띄워 운영 실행 설정을 확인합니다.
 *
 * <ul>
 *   <li>앞단 프록시(nginx)가 넘기는 X-Forwarded-For·X-Forwarded-Proto를 믿어 실제 접속 IP와 HTTPS를 인식한다.</li>
 *   <li>종료할 때 진행 중인 요청과 스케줄러가 끝날 시간을 준다.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ServerRuntimeTest.RemoteAddrCaptureConfig.class)
class ServerRuntimeTest extends IntegrationTest {

    private static final String CLIENT_IP = "203.0.113.7";

    @LocalServerPort
    private int serverPort;

    @Autowired
    private Environment environment;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void clearCapturedAddress() {
        RemoteAddrCaptureConfig.LAST_REMOTE_ADDR.set(null);
    }

    @Test
    @DisplayName("프록시가 HTTPS로 받았다고 알리면(X-Forwarded-Proto: https) HSTS 헤더를 보낸다")
    void sendsHstsBehindTlsProxy() throws Exception {
        HttpResponse<String> response = get("/api/users/me", true);

        assertThat(response.headers().firstValue("Strict-Transport-Security")).isPresent();
    }

    @Test
    @DisplayName("프록시 없이 평문 HTTP로 직접 오면 HSTS 헤더를 보내지 않는다")
    void doesNotSendHstsOverPlainHttp() throws Exception {
        HttpResponse<String> response = get("/api/users/me", false);

        assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
    }

    @Test
    @DisplayName("프록시가 넘긴 X-Forwarded-For의 실제 접속 IP를 요청 주소로 본다")
    void usesForwardedClientAddress() throws Exception {
        get("/api/users/me", true);

        assertThat(RemoteAddrCaptureConfig.LAST_REMOTE_ADDR.get()).isEqualTo(CLIENT_IP);
    }

    @Test
    @DisplayName("종료할 때 진행 중인 요청을 기다리고, 종료 단계마다 최대 40초를 준다")
    void shutsDownGracefullyWithinFortySecondsPerPhase() {
        assertThat(environment.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("40s");
    }

    private HttpResponse<String> get(String path, boolean viaTlsProxy) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + path)).GET();
        if (viaTlsProxy) {
            request.header("X-Forwarded-For", CLIENT_IP)
                    .header("X-Forwarded-Proto", "https");
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 요청 주소를 가장 먼저 기록하는 필터. 보안 필터보다 앞에 두어 401로 끝나는 요청도 기록한다.
     */
    @TestConfiguration
    static class RemoteAddrCaptureConfig {

        static final AtomicReference<String> LAST_REMOTE_ADDR = new AtomicReference<>();

        @Bean
        FilterRegistrationBean<OncePerRequestFilter> remoteAddrCaptureFilter() {
            OncePerRequestFilter filter = new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                    LAST_REMOTE_ADDR.set(request.getRemoteAddr());
                    chain.doFilter(request, response);
                }
            };
            FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }
}
