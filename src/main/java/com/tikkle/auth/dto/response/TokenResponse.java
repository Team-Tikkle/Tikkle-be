package com.tikkle.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 응답")
public record TokenResponse(
        @Schema(description = "액세스 토큰") String accessToken,
        @Schema(description = "리프레시 토큰") String refreshToken,
        @Schema(description = "신규 가입 유저 여부 (true: 이번 로그인으로 회원가입됨)", example = "false") boolean isNewUser
) {}