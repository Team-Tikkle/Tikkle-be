package com.tikkle.auth.client;

import com.tikkle.auth.exception.InvalidSocialTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GoogleOAuthClient {
    private final RestClient restClient = RestClient.create();
    private final String userInfoUri;

    public GoogleOAuthClient(@Value("${oauth.google.user-info-uri}") String userInfoUri) {
        this.userInfoUri = userInfoUri;
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
