package com.tikkle.auth.client;

import com.tikkle.auth.exception.InvalidSocialTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 구글 OAuth API를 호출하여 사용자 정보를 획득하는 외부 연동 클라이언트입니다.
 */
@Slf4j
@Component
public class GoogleOAuthClient {
    private final RestClient restClient;
    private final String userInfoUri;

    public GoogleOAuthClient(@Value("${oauth.google.user-info-uri}") String userInfoUri) {
        this.userInfoUri = userInfoUri;

        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 프론트엔드로부터 전달받은 구글 액세스 토큰을 이용해 구글 서버에서 사용자 정보를 조회합니다.
     *
     * @param accessToken 구글 액세스 토큰
     * @return 구글에서 응답받은 사용자 프로필 정보 (이메일, 이름 등)
     * @throws InvalidSocialTokenException 토큰이 유효하지 않거나 구글 서버 요청에 실패한 경우
     */
    public GoogleUserInfo getUserInfo(String accessToken) {
        return restClient.get()
                .uri(userInfoUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    try {
                        String errorBody = new String(response.getBody().readAllBytes());
                        log.error("[GoogleOAuthClient] 구글 사용자 정보 요청 실패 - Status: {}, Response: {}", 
                                response.getStatusCode(), errorBody);
                    } catch (Exception e) {
                        log.error("[GoogleOAuthClient] 구글 사용자 정보 요청 실패 (에러 바디 읽기 실패) - Status: {}", response.getStatusCode(), e);
                    }
                    throw new InvalidSocialTokenException();
                })
                .body(GoogleUserInfo.class);
    }
}