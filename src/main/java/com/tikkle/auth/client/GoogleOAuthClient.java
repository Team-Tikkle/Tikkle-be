package com.tikkle.auth.client;

import com.tikkle.auth.exception.InvalidSocialTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

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

    public GoogleUserInfo getUserInfo(String accessToken) {
        return restClient.get()
                .uri(userInfoUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new InvalidSocialTokenException();
                })
                .body(GoogleUserInfo.class);
    }
}