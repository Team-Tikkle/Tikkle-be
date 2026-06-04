package com.tikkle.auth.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 구글 userinfo 엔드포인트 응답 매핑
 * (https://www.googleapis.com/oauth2/v3/userinfo)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleUserInfo(
        String sub,
        String email,
        String name
) {}
